package com.badminton.bot;

import com.badminton.bot.service.AnnouncementImageService;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementImageServiceTest {

    @Test
    void rendersJpegWithExpectedSize() throws Exception {
        AnnouncementImageService service = new AnnouncementImageService();
        byte[] bytes = service.render(LocalDate.of(2026, 8, 12));

        assertThat(bytes).isNotEmpty();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image.getWidth()).isEqualTo(1280);
        assertThat(image.getHeight()).isEqualTo(1280);

        Path out = Path.of("tmp", "test-announcement.jpg");
        Files.createDirectories(out.getParent());
        Files.write(out, bytes);
    }
}
