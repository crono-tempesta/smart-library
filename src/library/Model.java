package library;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 도서관의 도서, 회원, 대출 및 예약 데이터 모델. */
public final class Model {
    private Model() { }

    /** 소장 도서 한 권. ID는 변경하지 않는다. */
    public static final class Book {
        public String id, title, author, publisher, category;
        public boolean lost;
        public Book(String id, String title, String author, String publisher, String category) {
            this.id=id; this.title=title; this.author=author; this.publisher=publisher; this.category=category;
        }
    }
    /** 회원별 연속 정시 반납 횟수와 제재 종료일. 종료일 당일부터 이용 가능하다. */
    public static final class Member {
        public String id, name, contact;
        public int streak;
        public LocalDate suspendedUntil;
        public Member(String id, String name, String contact) { this.id=id; this.name=name; this.contact=contact; }
        /** @return 연속 정시 반납 횟수에 따른 3, 5, 7권의 대출 한도 */
        public int limit() { return streak>=10 ? 7 : streak>=5 ? 5 : 3; }
    }
    /** 대출 이력. returned가 null이면 미반납이다. */
    public static final class Loan {
        public String id, memberId, bookId;
        public LocalDate borrowed, due, returned;
        public int renewals;
        public Loan(String id, String memberId, String bookId, LocalDate borrowed, LocalDate due) {
            this.id=id; this.memberId=memberId; this.bookId=bookId; this.borrowed=borrowed; this.due=due;
        }
    }
    /** 삽입 순서대로 처리하는 예약. 보관 기한이 null이면 대기 중이다. */
    public static final class Reservation {
        public String id, memberId, bookId;
        public LocalDate created, holdUntil;
        public Reservation(String id, String memberId, String bookId, LocalDate created) {
            this.id=id; this.memberId=memberId; this.bookId=bookId; this.created=created;
        }
    }
    /** 파일에 함께 저장되는 일관된 데이터 묶음. */
    public static final class State {
        public Map<String,Book> books=new LinkedHashMap<>();
        public Map<String,Member> members=new LinkedHashMap<>();
        public Map<String,Loan> loans=new LinkedHashMap<>();
        public List<Reservation> reservations=new ArrayList<>();
        public long nextId=1;
    }
    /** 베타의 시연용 권한 컨텍스트. 운영 환경의 로그인 인증을 대체하지 않는다. */
    public record Session(boolean admin, String memberId) { }
    /** 반납 처리의 구조화된 결과. */
    public record ReturnSummary(long overdueDays, int streak, int limit, LocalDate suspendedUntil) { }
}
