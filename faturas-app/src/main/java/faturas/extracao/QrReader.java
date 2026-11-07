package faturas.extracao;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Lê todos os QR codes de uma imagem (ZXing). */
public final class QrReader {

    private static final Map<DecodeHintType, Object> HINTS = Map.of(
            DecodeHintType.TRY_HARDER, Boolean.TRUE,
            DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));

    private QrReader() {}

    public static List<String> ler(BufferedImage imagem) {
        LuminanceSource fonte = new BufferedImageLuminanceSource(imagem);
        Binarizer[] tentativas = {new HybridBinarizer(fonte), new GlobalHistogramBinarizer(fonte)};
        for (Binarizer b : tentativas) {
            try {
                Result[] resultados = new QRCodeMultiReader().decodeMultiple(new BinaryBitmap(b), HINTS);
                return Arrays.stream(resultados).map(Result::getText).toList();
            } catch (NotFoundException e) {
                // tenta o binarizador seguinte
            }
        }
        return List.of();
    }
}
