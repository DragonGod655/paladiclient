import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class CropChams {
    public static void main(String[] args) {
        try {
            BufferedImage large = ImageIO.read(new File("src/main/resources/assets/paladi-client/textures/chams.jpg"));
            if (large == null) {
                System.out.println("Could not load chams.jpg");
                return;
            }
            // Crop 100x100 pixels around (251, 656) or search for matching boundary
            // Let's crop a 300x300 area around (200, 600)
            int cropX = Math.max(0, 251 - 100);
            int cropY = Math.max(0, 656 - 100);
            int cropW = Math.min(300, large.getWidth() - cropX);
            int cropH = Math.min(300, large.getHeight() - cropY);
            
            BufferedImage crop = large.getSubimage(cropX, cropY, cropW, cropH);
            ImageIO.write(crop, "png", new File("C:\\Users\\Admin\\.gemini\\antigravity-ide\\brain\\b9dca162-63f2-4bdf-9fd0-cd7f59933623\\chams_crop.png"));
            System.out.println("Cropped successfully to chams_crop.png");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
