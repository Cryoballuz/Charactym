import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Charactym 图标生成工具：
 * 把用户提供的原始图片生成 Android 全套启动图标——
 * 1) mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png 与 ic_launcher_round.png（48~192px 正方形，中心裁剪）
 * 2) 自适应图标前景 ic_launcher_foreground.png（108dp 各密度：图片等比缩放到 66% 安全区居中，透明背景）
 * 3) 输出边框平均色，作为自适应图标背景色（打印到 stdout，手动填入 ic_launcher_background.xml）
 *
 * 用法：java IconResizer <源图片> <res目录>
 */
public class IconResizer {

    public static void main(String[] args) throws Exception {
        File srcFile = new File(args[0]);
        File resDir = new File(args[1]);

        BufferedImage src = ImageIO.read(srcFile);
        if (src == null) {
            System.err.println("无法读取图片: " + args[0]);
            System.exit(1);
        }
        System.out.println("源图尺寸: " + src.getWidth() + "x" + src.getHeight());

        // 1) 传统方形图标（中心裁剪为正方形）
        BufferedImage square = centerSquare(src);
        int[] legacySizes = {48, 72, 96, 144, 192};
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (int i = 0; i < legacySizes.length; i++) {
            int s = legacySizes[i];
            String d = densities[i];
            save(scale(square, s, s), new File(resDir, "mipmap-" + d + "/ic_launcher.png"));
            save(scale(square, s, s), new File(resDir, "mipmap-" + d + "/ic_launcher_round.png"));
        }

        // 2) 自适应图标前景（108dp 各密度，图片缩放到 66% 居中）
        int[] fgSizes = {108, 162, 216, 324, 432};
        for (int i = 0; i < fgSizes.length; i++) {
            int s = fgSizes[i];
            String d = densities[i];
            BufferedImage canvas = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            int box = (int) (s * 0.66);
            double k = Math.min((double) box / src.getWidth(), (double) box / src.getHeight());
            int iw = Math.max(1, (int) (src.getWidth() * k));
            int ih = Math.max(1, (int) (src.getHeight() * k));
            BufferedImage fit = scale(src, iw, ih);
            g.drawImage(fit, (s - iw) / 2, (s - ih) / 2, null);
            g.dispose();
            save(canvas, new File(resDir, "mipmap-" + d + "/ic_launcher_foreground.png"));
        }

        // 3) 边框平均色（自适应图标背景用）
        Color bg = borderAverage(src);
        System.out.println("建议背景色: " + String.format("#%02X%02X%02X", bg.getRed(), bg.getGreen(), bg.getBlue()));
        System.out.println("完成。");
    }

    static BufferedImage centerSquare(BufferedImage src) {
        int s = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - s) / 2;
        int y = (src.getHeight() - s) / 2;
        return src.getSubimage(x, y, s, s);
    }

    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    static Color borderAverage(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long r = 0, g = 0, b = 0;
        long n = 0;
        for (int x = 0; x < w; x++) {
            for (int y : new int[]{0, h - 1}) {
                int rgb = img.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
                n++;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x : new int[]{0, w - 1}) {
                int rgb = img.getRGB(x, y);
                r += (rgb >> 16) & 0xFF;
                g += (rgb >> 8) & 0xFF;
                b += rgb & 0xFF;
                n++;
            }
        }
        return new Color((int) (r / n), (int) (g / n), (int) (b / n));
    }

    static void save(BufferedImage img, File f) throws Exception {
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println("写出: " + f.getPath());
    }
}
