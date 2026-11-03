package library;

import static library.Model.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** 한국어 Java Swing 베타 화면. 시연 역할 선택과 도서관 업무 화면을 제공한다. */
public final class LibraryFrame extends JFrame {
    private final LibraryService service;
    private final JComboBox<String> role = new JComboBox<>(new String[] { "회원 시연", "관리자 시연" });
    private final JComboBox<String> members = new JComboBox<>();
    private final JLabel summary = new JLabel(), status = new JLabel(" ");
    private final JTextField search = new JTextField(24);
    private final JCheckBox available = new JCheckBox("대출 가능만");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTable books = table("도서 ID", "서명", "저자", "출판사", "분류", "상태");
    private final JTable loans = table("대출 ID", "회원", "도서", "대출일", "반납 예정일", "반납일", "연장 횟수");
    private final JTable reservations = table("예약 ID", "회원", "도서", "순번", "상태", "보관 종료일");
    private final JTable memberTable = table("회원 ID", "이름", "연락처", "성실 반납", "대출 한도", "제재 종료일");
    private final JTable popular = table("순위", "도서", "대출 횟수"), readers = table("순위", "회원", "대출 횟수"),
            overdue = table("회원", "도서", "반납 예정일", "연체 일수");
    private final JPanel memberPanel = new JPanel(new BorderLayout(8, 8)),
            statsPanel = new JPanel(new GridLayout(3, 1, 0, 12));
    private final java.util.List<JButton> adminButtons = new ArrayList<>();
    private boolean updating;
    private final javax.swing.Timer timer;

    public LibraryFrame(LibraryService service, Path dataPath) {
        super("Smart Library | 도서관 대출관리 베타");
        this.service = service;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1220, 820);
        setMinimumSize(new Dimension(960, 660));
        setLocationRelativeTo(null);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(20, 22, 14, 22));
        root.setBackground(Color.WHITE);
        setContentPane(root);
        JPanel head = new JPanel(new BorderLayout(14, 8));
        head.setOpaque(false);
        JLabel title = new JLabel("스마트 도서관");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25));
        head.add(title, BorderLayout.WEST);
        JPanel who = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        who.setOpaque(false);
        who.add(new JLabel("시연 역할"));
        who.add(role);
        who.add(new JLabel("업무 대상 회원"));
        who.add(members);
        head.add(who, BorderLayout.EAST);
        summary.setBorder(new EmptyBorder(12, 0, 3, 0));
        head.add(summary, BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);
        JPanel bookPanel = new JPanel(new BorderLayout(8, 10));
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        filter.add(new JLabel("서명 · 저자 · ID"));
        filter.add(search);
        filter.add(available);
        filter.add(button("검색", () -> refresh(false)));
        bookPanel.add(filter, BorderLayout.NORTH);
        bookPanel.add(new JScrollPane(books));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actions.add(button("대출", () -> {
            String id = service.checkout(session(), memberId(), selected(books));
            refresh(false);
            notice("대출 완료: " + id + " / 반납 예정일 " + service.today().plusDays(14));
        }));
        actions.add(button("예약", () -> {
            int pos = service.reserve(session(), memberId(), selected(books));
            refresh(false);
            notice("예약 완료: " + pos + "순위");
        }));
        actions.add(adminButton("도서 등록", () -> editBook(true)));
        actions.add(adminButton("도서 수정", () -> editBook(false)));
        actions.add(adminButton("도서 삭제", () -> {
            String id = selected(books);
            if (confirm("선택 도서를 삭제할까요?")) {
                service.deleteBook(session(), id);
                refresh(false);
            }
        }));
        bookPanel.add(actions, BorderLayout.SOUTH);
        tabs.addTab("도서 검색", bookPanel);
        JPanel loanPanel = new JPanel(new BorderLayout(8, 8));
        loanPanel.add(new JScrollPane(loans));
        JPanel la = new JPanel(new FlowLayout(FlowLayout.LEFT));
        la.add(button("7일 연장", () -> {
            java.time.LocalDate due = service.renew(session(), selected(loans));
            refresh(false);
            notice("연장 완료: " + due);
        }));
        la.add(button("반납 처리", () -> {
            String id = selected(loans);
            if (!confirm("선택한 도서를 반납 처리할까요?"))
                return;
            ReturnSummary r = service.returnBook(session(), id);
            refresh(false);
            notice("반납 완료 / 연체 " + r.overdueDays() + "일 / 한도 " + r.limit() + "권 / 제재 종료일 " + show(r.suspendedUntil()));
        }));
        loanPanel.add(la, BorderLayout.SOUTH);
        tabs.addTab("대출 · 반납", loanPanel);
        JPanel rp = new JPanel(new BorderLayout());
        rp.add(new JScrollPane(reservations));
        JPanel ra = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ra.add(button("예약 취소", () -> {
            String id = selected(reservations);
            if (confirm("선택 예약을 취소할까요?")) {
                service.cancelReservation(session(), id);
                refresh(false);
            }
        }));
        rp.add(ra, BorderLayout.SOUTH);
        tabs.addTab("예약 대기열", rp);
        memberPanel.add(new JScrollPane(memberTable));
        JPanel ma = new JPanel(new FlowLayout(FlowLayout.LEFT));
        ma.add(button("회원 등록", () -> editMember(true)));
        ma.add(button("회원 수정", () -> editMember(false)));
        ma.add(button("회원 삭제", () -> {
            String id = selected(memberTable);
            if (confirm("선택 회원을 삭제할까요?")) {
                service.deleteMember(session(), id);
                refresh(true);
            }
        }));
        memberPanel.add(ma, BorderLayout.SOUTH);
        statsPanel.add(titled("인기 도서 TOP 10", popular));
        statsPanel.add(titled("다독 회원 TOP 10", readers));
        statsPanel.add(titled("현재 연체 도서", overdue));
        root.add(tabs);
        JPanel foot = new JPanel(new BorderLayout());
        foot.add(status);
        JLabel meta = new JLabel("데이터: " + dataPath.toAbsolutePath());
        meta.setFont(meta.getFont().deriveFont(11f));
        meta.setToolTipText(dataPath.toAbsolutePath().toString());
        foot.add(meta, BorderLayout.SOUTH);
        root.add(foot, BorderLayout.SOUTH);
        role.addActionListener(e -> run(() -> refresh(false)));
        members.addActionListener(e -> {
            if (!updating)
                run(() -> refresh(false));
        });
        search.addActionListener(e -> run(() -> refresh(false)));
        available.addActionListener(e -> run(() -> refresh(false)));
        run(() -> refresh(true));
        timer = new javax.swing.Timer(60000, e -> run(() -> {
            service.refreshHolds();
            refresh(false);
        }));
        timer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                timer.stop();
            }
        });
    }

    private static String show(Object o) {
        return o == null ? "-" : o.toString();
    }

    private Session session() {
        return new Session(role.getSelectedIndex() == 1, memberId());
    }

    private String memberId() {
        Object value = members.getSelectedItem();
        if (value == null)
            return "";
        return value.toString().split(" · ", 2)[0];
    }

    private static JTable table(String... cols) {
        DefaultTableModel m = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable t = new JTable(m);
        t.setRowHeight(32);
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setShowVerticalLines(false);
        t.setGridColor(new Color(225, 229, 229));
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setPreferredSize(new Dimension(100, 34));
        return t;
    }

    private static JPanel titled(String text, JTable table) {
        JPanel p = new JPanel(new BorderLayout(0, 5));
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.NORTH);
        p.add(new JScrollPane(table));
        return p;
    }

    private static String selected(JTable t) {
        int row = t.getSelectedRow();
        if (row < 0)
            throw new IllegalArgumentException("목록에서 항목을 선택하세요.");
        return t.getValueAt(row, 0).toString();
    }

    @FunctionalInterface
    private interface Action {
        void perform() throws Exception;
    }

    private void run(Action action) {
        try {
            action.perform();
        } catch (Exception ex) {
            status.setForeground(new Color(157, 45, 51));
            status.setText(ex.getMessage());
            JOptionPane.showMessageDialog(this, ex.getMessage(), "처리할 수 없습니다", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JButton button(String label, Action action) {
        JButton b = new JButton(label);
        b.addActionListener(e -> run(action));
        return b;
    }

    private JButton adminButton(String label, Action action) {
        JButton b = button(label, action);
        adminButtons.add(b);
        return b;
    }

    private boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(this, message, "확인",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    private void notice(String text) {
        status.setForeground(new Color(32, 108, 79));
        status.setText(text);
        JOptionPane.showMessageDialog(this, text, "처리 결과", JOptionPane.INFORMATION_MESSAGE);
    }

    private static DefaultTableModel clear(JTable table) {
        DefaultTableModel m = (DefaultTableModel) table.getModel();
        m.setRowCount(0);
        return m;
    }

    private void refresh(boolean reloadMembers) throws IOException {
        if (updating)
            return;
        updating = true;
        try {
            State s = service.snapshot();
            String current = memberId();
            if (reloadMembers) {
                members.removeAllItems();
                for (Member m : s.members.values())
                    members.addItem(m.id + " · " + m.name);
                for (int i = 0; i < members.getItemCount(); i++)
                    if (members.getItemAt(i).startsWith(current + " · "))
                        members.setSelectedIndex(i);
            }
            Session a = session();
            Member who = s.members.get(a.memberId());
            long count = s.loans.values().stream().filter(l -> l.memberId.equals(a.memberId()) && l.returned == null)
                    .count();
            summary.setText(who == null ? "등록된 회원이 없습니다. 관리자 시연에서 회원을 등록하세요."
                    : who.name + "  |  대출 " + count + " / " + who.limit() + "권  |  성실 반납 " + who.streak
                            + "회  |  제재 종료일 " + show(who.suspendedUntil) + "  |  기준일 " + service.today());
            for (JButton b : adminButtons)
                b.setVisible(a.admin());
            if (a.admin() && tabs.indexOfComponent(memberPanel) < 0) {
                tabs.addTab("회원 관리", memberPanel);
                tabs.addTab("통계", statsPanel);
            }
            if (!a.admin()) {
                tabs.remove(memberPanel);
                tabs.remove(statsPanel);
            }
            DefaultTableModel bm = clear(books);
            String q = search.getText().trim().toLowerCase(Locale.ROOT);
            for (Book b : s.books.values()) {
                String st = service.bookStatus(s, b);
                if (!(b.id + " " + b.title + " " + b.author).toLowerCase(Locale.ROOT).contains(q)
                        || (available.isSelected() && !st.equals("대출 가능")))
                    continue;
                bm.addRow(new Object[] { b.id, b.title, b.author, b.publisher, b.category, st });
            }
            DefaultTableModel lm = clear(loans);
            for (Loan l : s.loans.values())
                if (a.admin() || l.memberId.equals(a.memberId()))
                    lm.addRow(new Object[] { l.id, s.members.get(l.memberId).name, s.books.get(l.bookId).title,
                            l.borrowed, l.due, show(l.returned), l.renewals });
            DefaultTableModel rm = clear(reservations);
            Map<String, Integer> positions = new HashMap<>();
            for (Reservation r : s.reservations) {
                int pos = positions.merge(r.bookId, 1, Integer::sum);
                if (a.admin() || r.memberId.equals(a.memberId()))
                    rm.addRow(new Object[] { r.id, s.members.get(r.memberId).name, s.books.get(r.bookId).title, pos,
                            r.holdUntil == null ? "대기" : "우선 보관", show(r.holdUntil) });
            }
            DefaultTableModel mm = clear(memberTable);
            if (a.admin())
                for (Member m : s.members.values())
                    mm.addRow(new Object[] { m.id, m.name, m.contact, m.streak, m.limit(), show(m.suspendedUntil) });
            DefaultTableModel pm = clear(popular), rr = clear(readers), om = clear(overdue);
            if (a.admin()) {
                int i = 1;
                for (Map.Entry<String, Long> e : service.popularBooks(a))
                    pm.addRow(new Object[] { i++, s.books.get(e.getKey()).title, e.getValue() });
                i = 1;
                for (Map.Entry<String, Long> e : service.activeReaders(a))
                    rr.addRow(new Object[] { i++, s.members.get(e.getKey()).name, e.getValue() });
                for (Loan l : s.loans.values())
                    if (l.returned == null && service.today().isAfter(l.due))
                        om.addRow(new Object[] { s.members.get(l.memberId).name, s.books.get(l.bookId).title, l.due,
                                java.time.temporal.ChronoUnit.DAYS.between(l.due, service.today()) });
            }
            status.setForeground(new Color(80, 88, 88));
            status.setText("도서 " + s.books.size() + "권 · 회원 " + s.members.size() + "명");
        } finally {
            updating = false;
        }
    }

    private String[] form(String title, String[] labels, String[] values, boolean lockedId) {
        JPanel p = new JPanel(new GridLayout(labels.length, 2, 10, 10));
        JComponent[] inputs = new JComponent[labels.length];
        for (int i = 0; i < labels.length; i++) {
            p.add(new JLabel(labels[i]));
            if (labels[i].equals("분실 도서")) {
                JCheckBox check = new JCheckBox();
                check.setSelected(values[i].equals("분실"));
                inputs[i] = check;
            } else {
                JTextField field = new JTextField(values[i], 25);
                if (i == 0 && lockedId)
                    field.setEditable(false);
                inputs[i] = field;
            }
            p.add(inputs[i]);
        }
        if (JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
            return null;
        String[] result = new String[labels.length];
        for (int i = 0; i < labels.length; i++)
            result[i] = inputs[i] instanceof JCheckBox check ? (check.isSelected() ? "분실" : "정상")
                    : ((JTextField) inputs[i]).getText();
        return result;
    }

    private void editBook(boolean create) throws IOException {
        Book old = create ? null : service.snapshot().books.get(selected(books));
        String[] v = form(create ? "도서 등록" : "도서 수정", new String[] { "도서 ID", "서명", "저자", "출판사", "분류", "분실 도서" },
                old == null ? new String[] { "", "", "", "", "", "정상" }
                        : new String[] { old.id, old.title, old.author, old.publisher, old.category,
                                old.lost ? "분실" : "정상" },
                !create);
        if (v != null) {
            Book b = new Book(v[0], v[1], v[2], v[3], v[4]);
            b.lost = v[5].equals("분실");
            service.saveBook(session(), b, create);
            refresh(false);
        }
    }

    private void editMember(boolean create) throws IOException {
        Member old = create ? null : service.snapshot().members.get(selected(memberTable));
        String[] v = form(create ? "회원 등록" : "회원 수정", new String[] { "회원 ID", "이름", "연락처" },
                old == null ? new String[] { "", "", "" } : new String[] { old.id, old.name, old.contact }, !create);
        if (v != null) {
            service.saveMember(session(), new Member(v[0], v[1], v[2]), create);
            refresh(true);
        }
    }

    /**
     * 
     * @param folder PNG 파일을 저장할 디렉터리
     * @throws IOException 이미지 저장 실패
     */
    public void capture(Path folder) throws IOException {
        Files.createDirectories(folder);
        captureOne(folder.resolve("beta_member.png"));
        role.setSelectedIndex(1);
        tabs.setSelectedComponent(statsPanel);
        validate();
        captureOne(folder.resolve("beta_admin.png"));
        tabs.setSelectedIndex(1);
        validate();
        captureOne(folder.resolve("beta_loans.png"));
    }

    private void captureOne(Path target) throws IOException {
        validate();
        BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        printAll(g);
        g.dispose();
        ImageIO.write(image, "png", target.toFile());
    }
}
