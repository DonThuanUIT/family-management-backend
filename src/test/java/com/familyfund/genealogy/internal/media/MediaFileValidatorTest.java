package com.familyfund.genealogy.internal.media;

import com.familyfund.core.storage.MediaException;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaFileValidatorTest {
    private final MediaFileValidator validator = new MediaFileValidator();
    static MockMultipartFile png(int width,int height) throws IOException {
        var bytes = new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB),"png",bytes);
        return new MockMultipartFile("file","portrait.png","image/png",bytes.toByteArray());
    }
    @Test void normalizesAvatarToJpeg512() throws Exception {
        var result = validator.validate(png(128,128),true);
        var image = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertEquals("image/jpeg",result.mimeType());assertEquals(512,image.getWidth());assertEquals(512,image.getHeight());
    }
    @Test void keepsDocumentImageResolution() throws Exception {
        var result = validator.validate(png(640,800),false);
        assertEquals(800,ImageIO.read(new ByteArrayInputStream(result.bytes())).getHeight());
    }
    @Test void rejectsNonSquareAndSmallAvatars() throws Exception {
        var rectangle=png(128,256);var tiny=png(64,64);
        assertThrows(MediaException.class,()->validator.validate(rectangle,true));
        assertThrows(MediaException.class,()->validator.validate(tiny,true));
    }
    @Test void rejectsForgedMimeAndBrokenImage() {
        assertThrows(MediaException.class,()->validator.validate(new MockMultipartFile("file","fake.jpg","image/jpeg","<html>test</html>".getBytes()),true));
        assertThrows(MediaException.class,()->validator.validate(new MockMultipartFile("file","x.svg","image/svg+xml","<svg/>".getBytes()),false));
    }
    @Test void rejectsEmptyAndOversizedFiles() {
        assertThrows(MediaException.class,()->validator.validate(new MockMultipartFile("file",new byte[0]),true));
        var large=new MockMultipartFile("file","a.png","image/png",new byte[5*1024*1024+1]);
        assertEquals(413,assertThrows(MediaException.class,()->validator.validate(large,true)).getStatus());
    }
    @Test void acceptsOrdinaryPdfAndRejectsJavascript() throws Exception {
        try(var pdf=new PDDocument()) {
            pdf.addPage(new PDPage());var output=new ByteArrayOutputStream();pdf.save(output);
            assertEquals("application/pdf",validator.validate(new MockMultipartFile("file","doc.pdf","application/pdf",output.toByteArray()),false).mimeType());
            pdf.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('test')"));
            output.reset();pdf.save(output);
            var file=new MockMultipartFile("file","doc.pdf","application/pdf",output.toByteArray());
            assertThrows(MediaException.class,()->validator.validate(file,false));
        }
    }
}
