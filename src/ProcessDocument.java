import com.spire.pdf.FileFormat;
import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import com.spire.pdf.PdfPageRotateAngle;
import com.spire.pdf.annotations.PdfAnnotation;
import com.spire.pdf.annotations.PdfAnnotationCollection;
import com.spire.pdf.annotations.PdfTextWebLinkAnnotationWidget;
import com.spire.pdf.exporting.PdfImageInfo;
import com.spire.pdf.general.find.PdfTextFind;
import com.spire.pdf.general.find.PdfTextFindCollection;
import com.spire.pdf.graphics.*;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ProcessDocument implements Runnable {

    private PdfDocument doc;
    private File docFile;
    private String outPath;
    private List<String> textToRemove;
    private List<String> adsKeyWords;
    private String resPath;

    //TODO Create AD objects to keep track of ads, containing hyperlink and image or whatever and at the end analyze it
    // maybe hyperlink, iamge, pageIndex

    //TODO Create debug mode, saving before and after of every page, with every change made, like images removed etc.

    //TODO Parametrize pages to remove until figure out how to detect if 1 or 2

    //TODO Figure out how to execute wihtout intellij, im dumb

    public ProcessDocument(PdfDocument doc, File docFile, String outPath, List<String> adsKeyWords,
                           List<String> textToRemove, String resPath) {
        this.doc = doc;
        this.docFile = docFile;
        this.adsKeyWords = adsKeyWords;
        this.outPath = outPath;
        this.textToRemove = textToRemove;
        this.resPath = resPath;
    }

    @Override
    public void run() {
        analyzeDocument(doc, textToRemove);
        savePDF(docFile, doc, outPath, "");
        System.out.println(docFile + " processed successfully.");
    }

    /**
     * Check for and removes hyperlink ads, image ads and text ads.
     *
     * @param doc          pdf document to check.
     * @param textToRemove list of strings of text that are considered ads
     */
    private void analyzeDocument(PdfDocument doc, List<String> textToRemove) {
        List<PdfPageBase> pagesWithAds = new ArrayList<>();
        int pageIndex = 1;

        for (PdfPageBase page : (Iterable<PdfPageBase>) doc.getPages()) {
            int adsInThisPage = checkForAdsRemoveHyperlinks(page, pagesWithAds);
            System.out.println("Ads in page " + page + ": " + adsInThisPage);
            removeUnnecesaryText(page);
            removeAdImages(adsInThisPage, page, pageIndex);
            pageIndex++;
        }
    }

    /**
     * Searches the page given for hyperlink ads and removes them
     *
     * @param page         page to check
     * @param pagesWithAds List to keep log of every page with ads
     * @return number of ad hyperlinks detected.
     */
    private int checkForAdsRemoveHyperlinks(PdfPageBase page, List<PdfPageBase> pagesWithAds) {
        int adsInThisPage = 0;

        PdfAnnotationCollection widgetCollection = page.getAnnotationsWidget();
        for (int i = 0; i < widgetCollection.getCount(); i++) {
            PdfAnnotation annotation = widgetCollection.get(i);
            if (annotation instanceof PdfTextWebLinkAnnotationWidget) {
                for (String ad : adsKeyWords) {
                    try {
                        if (((PdfTextWebLinkAnnotationWidget) annotation).getUrl().contains(ad)) {
                            pagesWithAds.add(page);
                            adsInThisPage++;
                            //System.out.println(widgetCollection.getCount() + " ads found in page " + pageIndex*getFileNumber(docFile) + ": "
                            //+ ((PdfTextWebLinkAnnotationWidget) annotation).getUrl());
                            widgetCollection.removeAt(i);
                            i--;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return adsInThisPage;
    }

    /**
     * Removes ad images in the page, currently supports whole page ads and corner ads.
     *
     * @param adsInThisPage number of ad hyperlinks in this page
     * @param page          page with ad images
     * @param pageIndex     number of path received
     */
    private void removeAdImages(int adsInThisPage, PdfPageBase page, int pageIndex) {
        if (adsInThisPage == 2) {
            System.out.println("Removed corner ad in page: " + pageIndex);
            removeCornerAd(page);
            changePageMargins(doc, page, -150, 0, -90, -50);//TODO get these values from actual ads
        } else {
            existsEqualBoundsImage(page, "watermark.jpg", true);
            //if (adsInThisPage == 1 && (pageIndex == 1 || isImageOnlyPage(page))) {
            if (pageIndex == 1 || pageIndex == 1) {
                //Most probably is the starting page ad
                System.out.println("Removed page: " + pageIndex);
                doc.getPages().remove(page);
            }
//            else if (existsEqualBoundsImage(page, "banner.jpg", false)) {
//                //doc.getPages().remove(page);
//            }
        }
    }

    /**
     * Removes from the page every string of text that matches the ones in path PATH_TEXT_TO_REMOVE.
     * Can remove text at 0º and 90º rotation.
     *
     * @param page page where text to be removed is.
     */
    private void removeUnnecesaryText(PdfPageBase page) {
        //TODO maybe parametrizar en el txt si es wholeword, ignorecase o mas cosas
        if (textToRemove != null) {
            removeText(textToRemove, page);
            page.setRotation(PdfPageRotateAngle.Rotate_Angle_90);
            removeText(textToRemove, page);
            page.setRotation(PdfPageRotateAngle.Rotate_Angle_0);

            //Covers bottom of pdf with white rectangle to cover ad text
            Rectangle2D rec = new Rectangle(
                    (int) page.getActualSize().getWidth() / 8,
                    (int) page.getActualSize().getHeight() - 30,
                    (int) (page.getActualSize().getWidth() / 1.25),
                    50);
            page.getCanvas().drawRectangle(PdfBrushes.getWhite(), rec);
        }
    }

    /**
     * Removes from the page every string of text that matches the ones received.
     *
     * @param textToRemove specifies which strings of text to remove.
     * @param page         page with text.
     */
    private void removeText(List<String> textToRemove, PdfPageBase page) {
        for (String textRule : textToRemove) {
            PdfTextFindCollection collection = page.findText(textRule, false, true);
            PdfBrush brush = new PdfSolidBrush(new PdfRGBColor());
            PdfTrueTypeFont font = new PdfTrueTypeFont(new Font("Arial", Font.PLAIN, 12));
            Rectangle2D rec;
            for (PdfTextFind find : collection.getFinds()) {
                rec = find.getBounds();
                page.getCanvas().drawRectangle(PdfBrushes.getWhite(), rec);
                page.getCanvas().drawString("", font, brush, rec);
            }
        }
    }

    /**
     * Checks if the page is just an image with no text.
     *
     * @param page page to check
     * @return Whether or not the page provided is only composed of one image.
     */
    private boolean isImageOnlyPage(PdfPageBase page) {
        return page.getImagesInfo().length == 1 && page.extractText().length() <= 90;
        //TODO change 90 when I know how to delete wuolah disclaimers, they are 84chars long
    }

    /**
     * Check if the page contains an image with same bounds as the one provided, gives option to delete image.
     *
     * @param page      page to check
     * @param imageName image name with extension, must be in resources directory.
     * @param delete    delete image or not
     */
    private boolean existsEqualBoundsImage(PdfPageBase page, String imageName, boolean delete) {
        boolean found = false;
        PdfImage imageCompared = PdfImage.fromFile(resPath + "\\" + imageName);
        for (int i = 0; i < page.getImagesInfo().length && !found; i++) {
            PdfImageInfo image = page.getImagesInfo()[i];
            //Same dimensions
            if (Math.abs((image.getBounds().getWidth() / image.getBounds().getHeight())
                    - (imageCompared.getBounds().getWidth() / imageCompared.getBounds().getHeight()))
                    <= 0.001) {
                found = true;
                if (delete) {
                    try {
                        PdfImage emptyPixel = PdfImage.fromFile(resPath + "\\EmptyPixel.png");
                        page.replaceImage(i, emptyPixel);
                    } catch (IllegalArgumentException e) {
                        System.out.println("ERROR: FAILED TO DELETE IMAGE");
                        e.printStackTrace();
                    }
                }
            }
        }
        return found;
    }


    /**
     * Removes the two ads that compose the corner ad.
     *
     * @param page page that contains the ads.
     */
    //TODO: more conditions, like how close they are and how they touch corner up left
    private void removeCornerAd(PdfPageBase page) {
        for (int i = 0; i < page.getImagesInfo().length; i++) {
            PdfImageInfo image = page.getImagesInfo()[i];
            //Omit big images as they are most probably content, not ads
            if (image.getBounds().getHeight() * image.getBounds().getWidth() < 300000) {
                try {
                    PdfImage emptyPixel = PdfImage.fromFile(resPath + "\\EmptyPixel.png");
                    page.replaceImage(i, emptyPixel);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    //System.out.println(pdfImageInfo.getIndex());
                }
            }
        }
    }

    /**
     * Resizes the page margins, accepts both positive and negative numbers. //TODO Right now it changes with each file, need to generalize/automate somehow
     *
     * @param doc    doc where page is located
     * @param page   page to resize
     * @param top    top margin distance
     * @param bottom bottom margin distance
     * @param left   left margin distance
     * @param right  right margin distance
     */
    private void changePageMargins(PdfDocument doc, PdfPageBase page, float top, float bottom, float left, float right) {
        PdfPageBase newPage = doc.getPages().insert(doc.getPages().indexOf(page), page.getSize(), new PdfMargins(0));
        newPage.getCanvas().scaleTransform(
                (page.getActualSize().getWidth() - left - right) / page.getActualSize().getWidth(),
                (page.getActualSize().getHeight() - top - bottom) / page.getActualSize().getHeight());
        newPage.getCanvas().drawTemplate(page.createTemplate(), new Point2D.Float(left, top));
        doc.getPages().remove(page);
    }

    /**
     * Saves Pdf document, allows to add trailing name before .pdf
     *
     * @param inputPDF   File of original pdf
     * @param doc        pdfDocument data
     * @param outputPath Path of destiny
     * @param afterName  Optional string before .pdf
     */
    private void savePDF(File inputPDF, PdfDocument doc, String outputPath, String afterName) {
        String outputFilePath = outputPath
                + "\\"
                + inputPDF.getName().substring(0, inputPDF.getName().length() - 4)
                + afterName + ".pdf";
        doc.saveToFile(outputFilePath, FileFormat.PDF);
    }

    /**
     * Extracts last number from the file given
     *
     * @param file File with name with 3 last numbers
     * @return Only the 3 last numbers, without leading zeros
     */
    private int getFileNumber(File file) {
        String numString = file.getName().substring(file.getName().lastIndexOf('_') + 1, file.getName().lastIndexOf('_') + 4);
        return Integer.parseInt(numString);
    }
}
