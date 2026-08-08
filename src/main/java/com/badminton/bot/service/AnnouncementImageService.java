package com.badminton.bot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Собирает картинку анонса: шаблон без текста + «ЗАРЯДКА» + дата события.
 */
@Slf4j
@Service
public class AnnouncementImageService {

    private static final String TEMPLATE_PATH = "images/zaryadka-template.jpg";
    private static final String CLASSPATH_FONT = "fonts/DejaVuSans-Bold.ttf";
    private static final String SYSTEM_FONT = "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final String SAMPLE_CYRILLIC = "ЗАРЯДКА";

    private final Font baseFont;

    public AnnouncementImageService() {
        this.baseFont = loadFont();
        log.info("Шрифт анонса: {}", baseFont.getFontName());
    }

    public byte[] render(LocalDate eventDate) {
        try {
            BufferedImage template;
            try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream()) {
                template = ImageIO.read(in);
            }
            if (template == null) {
                throw new IllegalStateException("Не удалось прочитать шаблон " + TEMPLATE_PATH);
            }

            int w = template.getWidth();
            int h = template.getHeight();
            int footerY = findFooterY(template);
            int textAreaWidth = (int) (w * 0.62);

            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                g.drawImage(template, 0, 0, null);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setColor(Color.BLACK);

                Font titleFont = baseFont.deriveFont(Font.BOLD, 96f);
                Font dateFont = baseFont.deriveFont(Font.BOLD, 82f);
                String title = "ЗАРЯДКА";
                String date = eventDate.format(DATE_FORMAT);

                g.setFont(titleFont);
                FontMetrics titleMetrics = g.getFontMetrics();
                g.setFont(dateFont);
                FontMetrics dateMetrics = g.getFontMetrics();

                int gap = 28;
                int blockHeight = titleMetrics.getAscent() + gap + dateMetrics.getAscent();
                int footerHeight = h - footerY;
                int titleBaseline = footerY + (footerHeight - blockHeight) / 2 + titleMetrics.getAscent();
                int dateBaseline = titleBaseline + gap + dateMetrics.getAscent();
                int textBlockWidth = Math.max(titleMetrics.stringWidth(title), dateMetrics.stringWidth(date));
                int startX = Math.max(24, (textAreaWidth - textBlockWidth) / 2);

                g.setFont(titleFont);
                g.drawString(title, startX, titleBaseline);
                g.setFont(dateFont);
                g.drawString(date, startX, dateBaseline);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(out, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось собрать картинку анонса: " + e.getMessage(), e);
        }
    }

    private int findFooterY(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = h / 2; y < h; y++) {
            int rgb = image.getRGB(w / 2, y);
            int r = (rgb >> 16) & 0xff;
            int green = (rgb >> 8) & 0xff;
            int b = rgb & 0xff;
            if (r > 245 && green > 245 && b > 245) {
                return y;
            }
        }
        return h * 55 / 100;
    }

    private Font loadFont() {
        Font system = tryLoadFromFile(Path.of(SYSTEM_FONT));
        if (system != null) {
            return system;
        }
        Font classpath = tryLoadFromClasspath(CLASSPATH_FONT);
        if (classpath != null) {
            return classpath;
        }
        log.warn("Используем логический шрифт SansSerif");
        return new Font(Font.SANS_SERIF, Font.BOLD, 96);
    }

    private Font tryLoadFromFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, path.toFile());
            return acceptIfSupportsCyrillic(font, path.toString());
        } catch (Exception e) {
            log.warn("Не удалось загрузить шрифт {}: {}", path, e.getMessage());
            return null;
        }
    }

    private Font tryLoadFromClasspath(String classpath) {
        Path tempFont = null;
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            tempFont = Files.createTempFile("zaryadka-font-", ".ttf");
            Files.write(tempFont, in.readAllBytes());
            Font font = Font.createFont(Font.TRUETYPE_FONT, tempFont.toFile());
            return acceptIfSupportsCyrillic(font, classpath);
        } catch (Exception e) {
            log.warn("Не удалось загрузить шрифт {}: {}", classpath, e.getMessage());
            return null;
        } finally {
            if (tempFont != null) {
                try {
                    Files.deleteIfExists(tempFont);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private Font acceptIfSupportsCyrillic(Font font, String source) {
        GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
        Font probe = font.deriveFont(Font.BOLD, 48f);
        int missing = probe.canDisplayUpTo(SAMPLE_CYRILLIC);
        if (missing != -1) {
            log.warn("Шрифт {} не содержит кириллицу (missing at {}), пропускаем", source, missing);
            return null;
        }
        // прогрев метрик — ловит битые glyph-таблицы до публикации
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setFont(probe);
            g.getFontMetrics().stringWidth(SAMPLE_CYRILLIC);
        } finally {
            g.dispose();
        }
        log.info("Загружен шрифт анонса из {}", source);
        return font;
    }
}
