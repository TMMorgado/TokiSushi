package faturas.extracao;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** Converte uma página desenhada num JPEG pequeno, embutido na página web (data URI). */
public final class Previews {

    private Previews() {}

    public static String jpegDataUri(BufferedImage imagem, float qualidade) throws IOException {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = escritor.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(qualidade);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ImageOutputStream saida = ImageIO.createImageOutputStream(bytes)) {
                escritor.setOutput(saida);
                escritor.write(null, new IIOImage(imagem, null, null), param);
            }
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
        } finally {
            escritor.dispose();
        }
    }
}
