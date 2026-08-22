import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class FindSubImage {
    public static void main(String[] args) {
        try {
            BufferedImage large = ImageIO.read(new File("src/main/resources/assets/paladi-client/textures/chams.jpg"));
            BufferedImage small = ImageIO.read(new File("C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\b9dca162-63f2-4bdf-9fd0-cd7f59933623\\media__1780708580204.png"));
            
            if (large == null || small == null) {
                System.out.println("Could not load images.");
                return;
            }
            
            int lw = large.getWidth();
            int lh = large.getHeight();
            int sw = small.getWidth();
            int sh = small.getHeight();
            
            System.out.println("Large: " + lw + "x" + lh + ", Small: " + sw + "x" + sh);
            
            // Search for best match (with some tolerance due to JPG compression)
            int bestX = -1;
            int bestY = -1;
            long minDiff = Long.MAX_VALUE;
            
            for (int y = 0; y <= lh - sh; y++) {
                for (int x = 0; x <= lw - sw; x++) {
                    long diff = 0;
                    for (int sy = 0; sy < sh; sy++) {
                        for (int sx = 0; sx < sw; sx++) {
                            int rgb1 = large.getRGB(x + sx, y + sy);
                            int rgb2 = small.getRGB(sx, sy);
                            
                            int r1 = (rgb1 >> 16) & 0xff;
                            int g1 = (rgb1 >> 8) & 0xff;
                            int b1 = rgb1 & 0xff;
                            
                            int r2 = (rgb2 >> 16) & 0xff;
                            int g2 = (rgb2 >> 8) & 0xff;
                            int b2 = rgb2 & 0xff;
                            
                            diff += Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
                        }
                    }
                    if (diff < minDiff) {
                        minDiff = diff;
                        bestX = x;
                        bestY = y;
                    }
                }
            }
            
            System.out.println("Best match at (" + bestX + ", " + bestY + ") with difference " + minDiff + " (avg diff per pixel " + ((double)minDiff / (sw * sh * 3)) + ")");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
