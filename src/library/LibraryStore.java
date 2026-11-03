package library;

import static library.Model.*;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Properties;

/** UTF-8 XML 파일로 데이터를 저장한다. 임시 파일을 완성한 후 원본을 교체한다. */
public final class LibraryStore {
    private final Path file;

    public LibraryStore(Path file) {
        this.file = file;
    }

    /** @return 데이터 파일 경로 */
    public Path path() {
        return file;
    }

    /**
     * @return 파일이 없으면 빈 상태, 있으면 복원한 상태
     * @throws IOException 손상되거나 읽을 수 없는 파일인 경우
     */
    public State load() throws IOException {
        if (!Files.exists(file))
            return new State();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.loadFromXML(in);
        }
        try {
            return decode(p);
        } catch (RuntimeException ex) {
            throw new IOException("데이터 파일을 읽을 수 없습니다. 원본은 보존됩니다.", ex);
        }
    }

    /**
     * @param state 저장할 상태
     * @throws IOException 저장 실패 시 원본을 유지하고 예외를 반환
     */
    public void save(State state) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        Path tmp = Files.createTempFile(dir, "library-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(tmp)) {
                encode(state).storeToXML(out, "Smart Library beta data", "UTF-8");
            }
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    static State copy(State s) {
        return decode(encode(s));
    }

    private static void put(Properties p, String key, Object value) {
        p.setProperty(key, value == null ? "" : value.toString());
    }

    private static LocalDate date(Properties p, String key) {
        String s = p.getProperty(key, "");
        return s.isEmpty() ? null : LocalDate.parse(s);
    }

    private static int count(Properties p, String key) {
        return Integer.parseInt(p.getProperty(key, "0"));
    }

    private static Properties encode(State s) {
        Properties p = new Properties();
        put(p, "version", 1);
        put(p, "nextId", s.nextId);
        put(p, "books", s.books.size());
        int i = 0;
        for (Book b : s.books.values()) {
            String k = "b." + (i++) + ".";
            put(p, k + "id", b.id);
            put(p, k + "title", b.title);
            put(p, k + "author", b.author);
            put(p, k + "publisher", b.publisher);
            put(p, k + "category", b.category);
            put(p, k + "lost", b.lost);
        }
        put(p, "members", s.members.size());
        i = 0;
        for (Member m : s.members.values()) {
            String k = "m." + (i++) + ".";
            put(p, k + "id", m.id);
            put(p, k + "name", m.name);
            put(p, k + "contact", m.contact);
            put(p, k + "streak", m.streak);
            put(p, k + "until", m.suspendedUntil);
        }
        put(p, "loans", s.loans.size());
        i = 0;
        for (Loan l : s.loans.values()) {
            String k = "l." + (i++) + ".";
            put(p, k + "id", l.id);
            put(p, k + "member", l.memberId);
            put(p, k + "book", l.bookId);
            put(p, k + "borrowed", l.borrowed);
            put(p, k + "due", l.due);
            put(p, k + "returned", l.returned);
            put(p, k + "renewals", l.renewals);
        }
        put(p, "reservations", s.reservations.size());
        i = 0;
        for (Reservation r : s.reservations) {
            String k = "r." + (i++) + ".";
            put(p, k + "id", r.id);
            put(p, k + "member", r.memberId);
            put(p, k + "book", r.bookId);
            put(p, k + "created", r.created);
            put(p, k + "until", r.holdUntil);
        }
        return p;
    }

    private static State decode(Properties p) {
        if (!"1".equals(p.getProperty("version")))
            throw new IllegalArgumentException("지원하지 않는 데이터 버전");
        State s = new State();
        s.nextId = Long.parseLong(p.getProperty("nextId"));
        for (int i = 0; i < count(p, "books"); i++) {
            String k = "b." + i + ".";
            Book b = new Book(p.getProperty(k + "id"), p.getProperty(k + "title"), p.getProperty(k + "author"),
                    p.getProperty(k + "publisher"), p.getProperty(k + "category"));
            b.lost = Boolean.parseBoolean(p.getProperty(k + "lost"));
            s.books.put(b.id, b);
        }
        for (int i = 0; i < count(p, "members"); i++) {
            String k = "m." + i + ".";
            Member m = new Member(p.getProperty(k + "id"), p.getProperty(k + "name"), p.getProperty(k + "contact"));
            m.streak = count(p, k + "streak");
            m.suspendedUntil = date(p, k + "until");
            s.members.put(m.id, m);
        }
        for (int i = 0; i < count(p, "loans"); i++) {
            String k = "l." + i + ".";
            Loan l = new Loan(p.getProperty(k + "id"), p.getProperty(k + "member"), p.getProperty(k + "book"),
                    date(p, k + "borrowed"), date(p, k + "due"));
            l.returned = date(p, k + "returned");
            l.renewals = count(p, k + "renewals");
            s.loans.put(l.id, l);
        }
        for (int i = 0; i < count(p, "reservations"); i++) {
            String k = "r." + i + ".";
            Reservation r = new Reservation(p.getProperty(k + "id"), p.getProperty(k + "member"),
                    p.getProperty(k + "book"), date(p, k + "created"));
            r.holdUntil = date(p, k + "until");
            s.reservations.add(r);
        }
        return s;
    }
}
