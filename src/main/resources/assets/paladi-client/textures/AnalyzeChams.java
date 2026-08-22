import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class AnalyzeChams {
    public static void main(String[] args) {
        try {
            File file = new File("src/main/resources/assets/paladi-client/textures/chams.jpg");
            if (!file.exists()) {
                System.out.println("File does not exist: " + file.getAbsolutePath());
                return;
            }
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                System.out.println("Could not read chams.jpg (null).");
                return;
            }
            System.out.println("Chams.jpg Width: " + img.getWidth() + ", Height: " + img.getHeight());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
