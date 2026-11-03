package library;

import static library.Model.*;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/** 대출 정책과 권한 검사, 예약 순서, 통계를 제공하는 핵심 서비스.
 * 변경은 복제 상태에 적용하고 파일 저장이 성공한 뒤 반영한다. */
public final class LibraryService {
    private final LibraryStore store;
    private final Clock clock;
    private State state;
    public LibraryService(LibraryStore store,Clock clock) throws IOException { this.store=store; this.clock=clock; this.state=store.load(); refreshHolds(); }
    public LocalDate today() { return LocalDate.now(clock); }
    /** @return UI에서 수정해도 원본에 영향이 없는 데이터 복사본 */
    public synchronized State snapshot() { return LibraryStore.copy(state); }
    private synchronized <T> T change(Function<State,T> op) throws IOException {
        State next=LibraryStore.copy(state); maintainHolds(next); T result=op.apply(next); store.save(next); state=next; return result;
    }
    private static void require(boolean condition,String msg) { if(!condition) throw new IllegalArgumentException(msg); }
    private static void admin(Session a) { require(a!=null && a.admin(),"관리자 권한이 필요합니다."); }
    private static void owns(Session a,String memberId) { require(a!=null && (a.admin() || memberId.equals(a.memberId())),"본인 내역만 처리할 수 있습니다."); }
    private static String text(String value,String label) { require(value!=null && !value.isBlank(),label+"을(를) 입력하세요."); require(value.trim().length()<=120,label+"은(는) 120자 이하로 입력하세요."); return value.trim(); }
    private static Book book(State s,String id) { Book b=s.books.get(id); require(b!=null,"도서를 찾을 수 없습니다."); return b; }
    private static Member member(State s,String id) { Member m=s.members.get(id); require(m!=null,"회원을 찾을 수 없습니다."); return m; }
    private static Loan loan(State s,String id) { Loan l=s.loans.get(id); require(l!=null && l.returned==null,"유효한 미반납 대출이 아닙니다."); return l; }
    private static long activeCount(State s,String id) { return s.loans.values().stream().filter(l->l.memberId.equals(id)&&l.returned==null).count(); }
    private static Loan activeLoan(State s,String id) { return s.loans.values().stream().filter(l->l.bookId.equals(id)&&l.returned==null).findFirst().orElse(null); }
    private boolean blocked(State s,String id) {
        Member m=member(s,id);
        return (m.suspendedUntil!=null && today().isBefore(m.suspendedUntil)) || s.loans.values().stream().anyMatch(l->l.memberId.equals(id)&&l.returned==null&&today().isAfter(l.due));
    }
    /** 도서 정보를 검증하여 등록 또는 수정한다.
     * @param actor 관리자 컨텍스트
     * @param value 도서 정보
     * @param create 등록이면 true, 수정이면 false
     * @throws IOException 파일 저장 실패 */
    public void saveBook(Session actor,Book value,boolean create) throws IOException {
        admin(actor); change(s->{ String id=text(value.id,"도서 ID"); require(create?!s.books.containsKey(id):s.books.containsKey(id),"도서 ID가 중복이거나 수정 대상이 없습니다.");
            Book b=new Book(id,text(value.title,"서명"),text(value.author,"저자"),text(value.publisher,"출판사"),text(value.category,"분류")); b.lost=value.lost;
            require(!b.lost || (activeLoan(s,id)==null && s.reservations.stream().noneMatch(r->r.bookId.equals(id))),"대출 또는 예약 중에는 분실 상태로 바꿀 수 없습니다."); s.books.put(id,b); return null; });
    }
    public void saveMember(Session actor,Member value,boolean create) throws IOException {
        admin(actor); change(s->{ String id=text(value.id,"회원 ID"); require(create?!s.members.containsKey(id):s.members.containsKey(id),"회원 ID가 중복이거나 수정 대상이 없습니다.");
            Member m=new Member(id,text(value.name,"이름"),text(value.contact,"연락처")); if(!create) { Member old=member(s,id); m.streak=old.streak; m.suspendedUntil=old.suspendedUntil; } s.members.put(id,m); return null; });
    }
    public void deleteBook(Session actor,String id) throws IOException { admin(actor); change(s->{ book(s,id); require(s.loans.values().stream().noneMatch(l->l.bookId.equals(id))&&s.reservations.stream().noneMatch(r->r.bookId.equals(id)),"이력이나 예약이 있는 도서는 삭제할 수 없습니다."); s.books.remove(id); return null; }); }
    public void deleteMember(Session actor,String id) throws IOException { admin(actor); change(s->{ member(s,id); require(s.loans.values().stream().noneMatch(l->l.memberId.equals(id))&&s.reservations.stream().noneMatch(r->r.memberId.equals(id)),"이력이나 예약이 있는 회원은 삭제할 수 없습니다."); s.members.remove(id); return null; }); }
    /** 14일 대출을 생성한다. 한도, 제재, 미반납 연체, 분실, 예약 우선권을 검사한다.
     * @param actor 시연 세션
     * @param memberId 대출 회원
     * @param bookId 소장 도서
     * @return 생성된 대출 ID
     * @throws IOException 저장 실패
     * @throws IllegalArgumentException 한도, 제재, 도서 상태 또는 권한 위반 */
    public String checkout(Session actor,String memberId,String bookId) throws IOException {
        owns(actor,memberId); return change(s->{ Member m=member(s,memberId); Book b=book(s,bookId);
            require(!blocked(s,memberId),"연체 또는 이용 정지 중입니다."); require(activeCount(s,memberId)<m.limit(),"대출 한도를 초과했습니다."); require(!b.lost && activeLoan(s,bookId)==null,"대출할 수 없는 도서입니다.");
            Reservation hold=s.reservations.stream().filter(r->r.bookId.equals(bookId)&&r.holdUntil!=null).findFirst().orElse(null);
            require(hold==null || hold.memberId.equals(memberId),"1순위 예약자에게 보관 중인 도서입니다.");
            if(hold!=null) s.reservations.remove(hold);
            String id="L"+s.nextId++; s.loans.put(id,new Loan(id,memberId,bookId,today(),today().plusDays(14))); return id; });
    }
    /** 예정일 이전이고 예약자가 없을 때 1회 7일 연장한다.
     * @param actor 시연 세션
     * @param loanId 미반납 대출 ID
     * @return 새 반납 예정일
     * @throws IOException 저장 실패
     * @throws IllegalArgumentException 예정일, 예약, 연장 횟수 또는 권한 위반 */
    public LocalDate renew(Session actor,String loanId) throws IOException {
        return change(s->{ Loan l=loan(s,loanId); owns(actor,l.memberId); require(!blocked(s,l.memberId),"연체 또는 이용 정지 중입니다."); require(today().isBefore(l.due),"반납 예정일 이전에만 연장할 수 있습니다."); require(l.renewals==0,"연장은 1회만 가능합니다."); require(s.reservations.stream().noneMatch(r->r.bookId.equals(l.bookId)),"예약 대기자가 있어 연장할 수 없습니다."); l.due=l.due.plusDays(7); l.renewals++; return l.due; });
    }
    /** 실제 시스템 날짜로 반납 처리한다. 정시 반납 혜택 또는 연체 제재를 적용한다.
     * @param actor 시연 세션
     * @param loanId 미반납 대출 ID
     * @return 연체일수, 성실 반납 횟수, 대출 한도 및 제재 종료일
     * @throws IOException 저장 실패
     * @throws IllegalArgumentException 이미 반납했거나 처리 권한이 없는 경우 */
    public ReturnSummary returnBook(Session actor,String loanId) throws IOException {
        return change(s->{ Loan l=loan(s,loanId); owns(actor,l.memberId); Member m=member(s,l.memberId); long days=Math.max(0,ChronoUnit.DAYS.between(l.due,today())); l.returned=today();
            if(days>0) { m.streak=0; LocalDate until=today().plusDays(days); if(m.suspendedUntil==null||until.isAfter(m.suspendedUntil))m.suspendedUntil=until; }
            else m.streak++;
            maintainHolds(s); return new ReturnSummary(days,m.streak,m.limit(),m.suspendedUntil); });
    }
    /** 대출 중인 도서의 예약 대기열 마지막에 등록한다.
     * @param actor 시연 세션
     * @param memberId 예약 회원
     * @param bookId 도서 ID
     * @return 현재 순번
     * @throws IOException 저장 실패
     * @throws IllegalArgumentException 중복 예약, 본인 대출 예약 또는 제재 상태 */
    public int reserve(Session actor,String memberId,String bookId) throws IOException {
        owns(actor,memberId); return change(s->{ member(s,memberId); book(s,bookId); Loan active=activeLoan(s,bookId);
            require(active!=null,"대출 중인 도서만 예약할 수 있습니다."); require(!active.memberId.equals(memberId),"본인이 대출한 도서는 예약할 수 없습니다."); require(!blocked(s,memberId),"연체 또는 이용 정지 중입니다.");
            require(s.reservations.stream().noneMatch(r->r.bookId.equals(bookId)&&r.memberId.equals(memberId)),"이미 예약한 도서입니다.");
            s.reservations.add(new Reservation("R"+s.nextId++,memberId,bookId,today())); return (int)s.reservations.stream().filter(r->r.bookId.equals(bookId)).count(); });
    }
    public void cancelReservation(Session actor,String id) throws IOException {
        change(s->{ Reservation r=s.reservations.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("예약을 찾을 수 없습니다.")); owns(actor,r.memberId); s.reservations.remove(r); maintainHolds(s); return null; });
    }
    /** 날짜 경과로 만료된 우선 보관을 정리한다. UI 갱신과 각 변경 작업에서 호출한다.
     * @throws IOException 저장 실패 */
    public synchronized void refreshHolds() throws IOException { change(s->null); }
    private void maintainHolds(State s) {
        for(Book b:s.books.values()) {
            if(activeLoan(s,b.id)!=null || b.lost) continue;
            LocalDate start=today();
            while(true) {
                Reservation r=s.reservations.stream().filter(x->x.bookId.equals(b.id)).findFirst().orElse(null); if(r==null)break;
                if(blocked(s,r.memberId)) { s.reservations.remove(r); continue; }
                if(r.holdUntil==null)r.holdUntil=start.plusDays(3);
                if(!today().isBefore(r.holdUntil)) { start=r.holdUntil; s.reservations.remove(r); continue; }
                break;
            }
        }
    }
    /** 도서 상태를 대출 및 예약 이력에서 계산한다.
     * @param s 조회용 상태
     * @param b 대상 도서
     * @return 계산된 도서 상태 */
    public String bookStatus(State s,Book b) {
        if(b.lost)return "분실"; Loan l=activeLoan(s,b.id);
        if(l!=null)return today().isAfter(l.due)?"연체":"대출 중";
        return s.reservations.stream().anyMatch(r->r.bookId.equals(b.id)&&r.holdUntil!=null)?"예약 보관":"대출 가능";
    }
    /** 전체 대출 이력으로 인기 도서를 집계한다.
     * @param actor 관리자 컨텍스트
     * @return 도서별 대출 횟수 상위 10건 */
    public synchronized List<Map.Entry<String,Long>> popularBooks(Session actor) { admin(actor); return rank(true); }
    /** 전체 대출 이력으로 다독 회원을 집계한다.
     * @param actor 관리자 컨텍스트
     * @return 회원별 누적 대출 횟수 상위 10건 */
    public synchronized List<Map.Entry<String,Long>> activeReaders(Session actor) { admin(actor); return rank(false); }
    private List<Map.Entry<String,Long>> rank(boolean books) {
        Map<String,Long> counts=new TreeMap<>(); for(Loan l:state.loans.values()) counts.merge(books?l.bookId:l.memberId,1L,Long::sum);
        return counts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).limit(10).toList();
    }
}
