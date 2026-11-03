package library;

import java.awt.*;
import java.nio.file.*;
import java.time.*;
import javax.swing.*;

/** 실행 가능한 JAR에서 한국어 도서관 베타를 시작하는 클래스. */
public final class Main {
    private Main() {
    }

    /**
     * @param args --data 경로, --demo 또는 --capture 출력폴더. --demo는 빈 파일에만 시연 데이터를 만든다.
     */
    public static void main(String[] args) throws Exception {
        Path file = Path.of(System.getProperty("user.home"), ".smart-library-beta", "library.xml"), capture = null;
        boolean demo = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--data" -> file = Path.of(args[++i]);
                case "--demo" -> demo = true;
                case "--capture" -> capture = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("알 수 없는 인수: " + args[i]);
            }
        }
        LibraryStore store = new LibraryStore(file);
        if (demo && !Files.exists(file))
            store.save(DemoData.create(LocalDate.now()));
        LibraryService service;
        try {
            service = new LibraryService(store, Clock.systemDefaultZone());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "데이터를 열 수 없습니다. 기존 파일은 보존됩니다.\n" + file + "\n" + ex.getMessage(),
                    "시작 오류", JOptionPane.ERROR_MESSAGE);
            return;
        }
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        Font font = new Font("Malgun Gothic", Font.PLAIN, 13);
        for (Object key : UIManager.getDefaults().keySet())
            if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
                UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
        Path finalCapture = capture, finalFile = file;
        SwingUtilities.invokeLater(() -> {
            LibraryFrame frame = new LibraryFrame(service, finalFile);
            frame.setVisible(true);
            if (finalCapture != null) {
                javax.swing.Timer timer = new javax.swing.Timer(800, e -> {
                    try {
                        frame.capture(finalCapture);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.exit(1);
                    } finally {
                        frame.dispose();
                    }
                });
                timer.setRepeats(false);
                timer.start();
            }
        });
    }
}
