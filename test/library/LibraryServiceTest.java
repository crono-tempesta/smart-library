package library;

import static library.Model.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 외부 테스트 라이브러리 없이 정책과 저장 결과를 검증한다. */
public final class LibraryServiceTest {
    private static int passed;
    private static final Session ADMIN = new Session(true, "M001"), U1 = new Session(false, "M001"),
            U2 = new Session(false, "M002"), U3 = new Session(false, "M003");

    private static class MutableClock extends Clock {
        LocalDate day = LocalDate.of(2026, 10, 26);

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return day.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }

    private record Fixture(LibraryService service, LibraryStore store, MutableClock clock) {
    }

    private static Fixture fixture() throws Exception {
        MutableClock c = new MutableClock();
        LibraryStore store = new LibraryStore(Files.createTempDirectory("library-test-").resolve("data.xml"));
        State s = DemoData.create(c.day);
        s.loans.clear();
        s.reservations.clear();
        s.members.get("M001").streak = 0;
        store.save(s);
        return new Fixture(new LibraryService(store, c), store, c);
    }

    private static void check(boolean value, String label) {
        if (!value)
            throw new AssertionError(label);
        System.out.println("PASS " + (++passed) + " " + label);
    }

    @FunctionalInterface
    private interface Task {
        void run() throws Exception;
    }

    private static void reject(Task t, String label) throws Exception {
        try {
            t.run();
        } catch (IllegalArgumentException expected) {
            check(true, label);
            return;
        }
        throw new AssertionError("Expected rejection: " + label);
    }

    public static void main(String[] args) throws Exception {
        Fixture f = fixture();
        LibraryService s = f.service;
        final LibraryService initial = s;
        String l = s.checkout(U1, "M001", "B001");
        check(s.snapshot().loans.get(l).due.equals(f.clock.day.plusDays(14)), "14-day checkout");
        reject(() -> initial.checkout(U2, "M002", "B001"), "already borrowed book");
        reject(() -> initial.checkout(U1, "M002", "B003"), "cross-member access");
        s.checkout(U1, "M001", "B002");
        s.checkout(U1, "M001", "B003");
        reject(() -> initial.checkout(U1, "M001", "B004"), "base limit 3");
        check(s.renew(U1, l).equals(f.clock.day.plusDays(21)), "renew by 7 days");
        reject(() -> initial.renew(U1, l), "only one renewal");
        check(s.reserve(U2, "M002", "B001") == 1, "FIFO first");
        check(s.reserve(U3, "M003", "B001") == 2, "FIFO second");
        reject(() -> initial.reserve(U2, "M002", "B001"), "duplicate reservation");
        reject(() -> initial.reserve(U1, "M001", "B001"), "own loan reservation");
        reject(() -> initial.reserve(U2, "M002", "B012"), "available book reservation");
        s.returnBook(U1, l);
        check(s.bookStatus(s.snapshot(), s.snapshot().books.get("B001")).equals("예약 보관"), "hold after return");
        reject(() -> initial.checkout(U3, "M003", "B001"), "priority holder only");
        check(s.checkout(U2, "M002", "B001") != null, "priority checkout");
        reject(() -> initial.returnBook(U1, l), "double return rejected");
        f = fixture();
        s = f.service;
        String id = s.checkout(U1, "M001", "B001");
        s.reserve(U2, "M002", "B001");
        final LibraryService reservedService = s;
        final String reservedLoan = id;
        reject(() -> reservedService.renew(U1, reservedLoan), "renewal blocked by reservation");
        s.reserve(U3, "M003", "B001");
        s.returnBook(U1, id);
        f.clock.day = f.clock.day.plusDays(3);
        s.refreshHolds();
        check(s.snapshot().reservations.get(0).memberId.equals("M003"), "hold transfers on expiry date");
        f.clock.day = f.clock.day.plusDays(3);
        s.refreshHolds();
        check(s.snapshot().reservations.isEmpty(), "expired queue cleared");
        f = fixture();
        s = f.service;
        for (int i = 0; i < 10; i++) {
            String loan = s.checkout(U1, "M001", "B001");
            s.returnBook(U1, loan);
            if (i == 4)
                check(s.snapshot().members.get("M001").limit() == 5, "five returns expands limit");
        }
        check(s.snapshot().members.get("M001").limit() == 7, "ten returns expands limit");
        id = s.checkout(U1, "M001", "B001");
        f.clock.day = f.clock.day.plusDays(17);
        final LibraryService lateService = s;
        reject(() -> lateService.checkout(U1, "M001", "B002"), "unreturned overdue blocks loans");
        ReturnSummary summary = s.returnBook(U1, id);
        check(summary.overdueDays() == 3 && summary.limit() == 3 && summary.streak() == 0, "overdue resets benefits");
        check(summary.suspendedUntil().equals(f.clock.day.plusDays(3)), "overdue suspension duration");
        reject(() -> lateService.checkout(U1, "M001", "B002"), "suspended loan denied");
        s.checkout(U2, "M002", "B002");
        reject(() -> lateService.reserve(U1, "M001", "B002"), "suspended reservation denied");
        f.clock.day = f.clock.day.plusDays(3);
        check(s.checkout(U1, "M001", "B003") != null, "suspension ends on boundary");
        LibraryService reloaded = new LibraryService(f.store, f.clock);
        check(reloaded.snapshot().loans.size() == s.snapshot().loans.size(), "persistence after restart");
        check(reloaded.snapshot().members.get("M001").streak == 0, "persist penalty");
        f = fixture();
        s = f.service;
        id = s.checkout(U1, "M001", "B001");
        f.clock.day = f.clock.day.plusDays(14);
        final LibraryService dueService = s;
        final String dueLoan = id;
        reject(() -> dueService.renew(U1, dueLoan), "no renewal on due date");
        check(s.returnBook(U1, id).overdueDays() == 0, "due date is on time");
        final LibraryService crud = s;
        reject(() -> crud.saveBook(U1, new Book("X", "x", "a", "p", "c"), true), "member cannot edit catalog");
        reject(() -> crud.deleteBook(ADMIN, "B001"), "preserve historic book");
        s.saveBook(ADMIN, new Book("NEW", "한글 & XML <검사>", "저자", "출판", "분류"), true);
        check(new LibraryService(f.store, f.clock).snapshot().books.get("NEW").title.contains("<검사>"),
                "Unicode XML round trip");
        s.saveMember(ADMIN, new Member("NEW", "회원", "contact"), true);
        s.deleteMember(ADMIN, "NEW");
        s.deleteBook(ADMIN, "NEW");
        check(!s.snapshot().books.containsKey("NEW"), "safe CRUD deletion");
        reject(() -> crud.popularBooks(U1), "statistics admin permission");
        check(s.popularBooks(ADMIN).get(0).getValue() == 1, "statistics aggregates loan history");
        f = fixture();
        s = f.service;
        id = s.checkout(U1, "M001", "B001");
        s.reserve(U2, "M002", "B001");
        s.reserve(U3, "M003", "B001");
        s.returnBook(U1, id);
        s.cancelReservation(U2, s.snapshot().reservations.get(0).id);
        check(s.snapshot().reservations.get(0).memberId.equals("M003")
                && s.snapshot().reservations.get(0).holdUntil != null, "cancel holder advances queue");
        State copy = s.snapshot();
        copy.books.clear();
        check(!s.snapshot().books.isEmpty(), "snapshot cannot mutate service");
        f = fixture();
        s = f.service;
        Path target = f.store.path();
        Files.delete(target);
        Files.createDirectory(target);
        final LibraryService failing = s;
        boolean failed = false;
        try {
            failing.checkout(U1, "M001", "B001");
        } catch (java.io.IOException expected) {
            failed = true;
        }
        check(failed && s.snapshot().loans.isEmpty(), "failed save does not commit memory");
        System.out.println("ALL " + passed + " CHECKS PASSED");
    }
}
