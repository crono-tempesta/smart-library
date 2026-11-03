package library;

import static library.Model.*;
import java.time.LocalDate;

/** 시연 전용 가상 데이터를 만든다. 실제 도서관 개인정보는 포함하지 않는다. */
public final class DemoData {
    private DemoData() {
    }

    /**
     * 가상 도서와 회원, 정상 대출과 연체 대출을 준비한다.
     * 
     * @param date 시연 기준일
     * @return 독립적인 초기 상태
     */
    public static State create(LocalDate date) {
        State s = new State();
        String[] titles = { "자료구조 입문", "객체지향 설계", "데이터베이스 기초", "운영체제 이해", "알고리즘 연습", "소프트웨어 테스트", "컴퓨터 네트워크",
                "Java 프로그래밍", "인공지능 개론", "웹 개발 기초", "시스템 분석", "프로그래밍 언어" };
        for (int i = 0; i < titles.length; i++) {
            String id = String.format("B%03d", i + 1);
            s.books.put(id, new Book(id, titles[i], "시연 저자 " + (i + 1), "학습출판", "컴퓨터공학"));
        }
        s.members.put("M001", new Member("M001", "이유민", "yumin@example.test"));
        s.members.put("M002", new Member("M002", "시연 회원 가", "member2@example.test"));
        s.members.put("M003", new Member("M003", "시연 회원 나", "member3@example.test"));
        s.members.get("M001").streak = 4;
        Loan ontime = new Loan("L1", "M001", "B001", date.minusDays(8), date.plusDays(6));
        s.loans.put(ontime.id, ontime);
        Loan overdue = new Loan("L2", "M002", "B002", date.minusDays(17), date.minusDays(3));
        s.loans.put(overdue.id, overdue);
        s.reservations.add(new Reservation("R3", "M003", "B002", date.minusDays(2)));
        s.nextId = 4;
        return s;
    }
}
