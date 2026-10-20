// Dementech · NFS Carbon — gera os assets de marca a partir do logo.
//
//   java tools/make_brand_assets.java <logo.png> <logotipo.png> <saida>
//
// Produz:
//   ic_launcher_foreground.png   432x432 (adaptive icon, camada do logo)
//   ic_launcher_monochrome.png   432x432 (silhueta branca, icone monocromatico)
//   ic_launcher_legacy.png       192x192 (icone legado quadrado)
//   brand_logo.png               1024x1024 (logo com alpha)
//   brand_logo_512.png           512x512
//
// Sem dependencias externas: usa apenas javax.imageio (roda no Termux com o JDK).
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public class make_brand_assets {
    static BufferedImage load(String path) throws Exception {
        BufferedImage src = ImageIO.read(new File(path));
        if (src == null) throw new IllegalArgumentException("nao consegui ler " + path);
        return src;
    }

    /** Redimensiona mantendo proporcao, centralizado em um canvas quadrado transparente. */
    static BufferedImage fit(BufferedImage src, int canvas, double scale) {
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double ratio = Math.min(canvas / (double) src.getWidth(), canvas / (double) src.getHeight());
        double k = ratio * scale;
        int w = (int) Math.round(src.getWidth() * k);
        int h = (int) Math.round(src.getHeight() * k);
        g.drawImage(src, (canvas - w) / 2, (canvas - h) / 2, w, h, null);
        g.dispose();
        return out;
    }

    /** Silhueta branca: preserva o alfa do original e pinta tudo de branco. */
    static BufferedImage silhouette(BufferedImage src, int canvas, double scale) {
        BufferedImage out = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        double ratio = Math.min(canvas / (double) src.getWidth(), canvas / (double) src.getHeight());
        double k = ratio * scale;
        int w = (int) Math.round(src.getWidth() * k);
        int h = (int) Math.round(src.getHeight() * k);
        BufferedImage tmp = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D t = tmp.createGraphics();
        t.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        t.drawImage(src, 0, 0, w, h, null);
        t.dispose();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = tmp.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a > 0) tmp.setRGB(x, y, (a << 24) | 0x00FFFFFF);
            }
        }
        g.drawImage(tmp, (canvas - w) / 2, (canvas - h) / 2, null);
        g.dispose();
        return out;
    }

    static void write(BufferedImage img, File out) throws Exception {
        File dir = out.getParentFile();
        if (dir != null) dir.mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("  " + out.getPath() + "  (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("uso: java make_brand_assets.java <logo.png> <logotipo.png> <saida>");
            System.exit(1);
        }
        String logoPath = args[0];
        String logoTipoPath = args[1];
        File out = new File(args[2]);
        out.mkdirs();

        BufferedImage logo = load(logoPath);
        BufferedImage logotipo = load(logoTipoPath);

        System.out.println("gerando assets de marca:");
        // Camada do logo: 66% do canvas (safe zone de 66dp em 108dp).
        write(fit(logotipo, 432, 0.66), new File(out, "ic_launcher_foreground.png"));
        write(silhouette(logotipo, 432, 0.66), new File(out, "ic_launcher_monochrome.png"));
        // Icone legado: fundo escuro + logo.
        BufferedImage legacy = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = legacy.createGraphics();
        g.setColor(new Color(0x0B0B0F));
        g.fillRect(0, 0, 192, 192);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int w = (int) (logotipo.getWidth() * 0.86), h = (int) (logotipo.getHeight() * 0.86);
        g.drawImage(logotipo, (192 - w) / 2, (192 - h) / 2, w, h, null);
        g.dispose();
        write(legacy, new File(out, "ic_launcher_legacy.png"));
        // Logos para a UI.
        write(fit(logotipo, 1024, 1.0), new File(out, "brand_logo.png"));
        write(fit(logotipo, 512, 1.0), new File(out, "brand_logo_512.png"));
        write(fit(logo, 512, 1.0), new File(out, "brand_mark_512.png"));
    }
}
