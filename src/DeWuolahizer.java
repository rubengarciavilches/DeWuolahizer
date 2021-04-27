import com.spire.pdf.PdfDocument;
import com.spire.pdf.PdfPageBase;
import com.spire.pdf.exporting.PdfImageInfo;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.multipdf.Splitter;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
//https://www.e-iceblue.com/Tutorials/Spire.PDF/Spire.PDF-Program-Guide/PDF-Link/Extract-and-Update-link-from-a-PDF-file.html

public class DeWuolahizer {
    private static final String INPUT_PATH = System.getProperty("user.dir") + "\\input";
    private static final String OUTPUT_PATH = System.getProperty("user.dir") + "\\output";
    private static final String TMP_INPUT_PATH = System.getProperty("user.dir") + "\\input\\tmp";
    private static final String TMP_OUTPUT_PATH = System.getProperty("user.dir") + "\\output\\tmp";
    private static final String TEXT_TO_REMOVE_PATH = System.getProperty("user.dir") + "\\text_to_remove.txt";
    private static final String ADS_KEYWORDS = System.getProperty("user.dir") + "\\ads_regex.txt";
    private static final String RESOURCES_PATH = System.getProperty("user.dir") + "\\resources";

    //TODO maybe remove protection... if any
    //TODO Log progress while doing it
    //TODO parametrize paths in a helper class, maybe get them from config txt for easier manipulation.
    //FIXME Apparently duplicate files break the program, as in "pedefe.pdf" and "pedefe (1).pdf"

    /**
     * Validates each PATH provided, verifies that directories are in fact directories and creates tmp
     * folders.
     */
    private static void checkPaths() {
        String[] paths = {INPUT_PATH, OUTPUT_PATH, TMP_INPUT_PATH, TMP_OUTPUT_PATH, RESOURCES_PATH};
        for (String path : paths) {
            File file = new File(path);
            if (!file.exists()) {
                file.mkdir();
            } else if (!file.isDirectory()) {
                throw new IllegalArgumentException("The " + path + " isn't a directory.");
            }
        }

    }

    /**
     * Processes every pdf file in the temporary folder in search for ad hyperlinks, ad images and ad texts,
     * and removes them.
     *
     * @param multiThreading Use multithreading or not. Very recommended to use it, as it reduces time to process
     *                       significantly.
     */
    private static void analyzeInput(boolean multiThreading) {
        prepareInput();
        File dir = new File(TMP_INPUT_PATH);
        File[] directoryListing = dir.listFiles();
        ExecutorService es = Executors.newCachedThreadPool();
        if (directoryListing != null) {
            if (multiThreading) {
                BenchmarkManager.startBenchmark(2, "AnalyzeMultithreading");
            } else {
                BenchmarkManager.startBenchmark(2, "AnalyzeMonothreading");
            }
            List<String> textToRemove = linesToList(TEXT_TO_REMOVE_PATH);
            List<String> adsKeyWords = linesToList(ADS_KEYWORDS);
            for (File child : directoryListing) {
                if (child.getName().endsWith(".pdf")) {
                    PdfDocument doc = new PdfDocument();
                    doc.loadFromFile(child.getAbsolutePath());


                    if (multiThreading) {
                        Runnable r = new ProcessDocument(doc, child, TMP_OUTPUT_PATH, adsKeyWords, textToRemove, RESOURCES_PATH);
                        es.execute(r);
                    } else {
                        Runnable r = new ProcessDocument(doc, child, TMP_OUTPUT_PATH, adsKeyWords, textToRemove, RESOURCES_PATH);
                        Thread thread = new Thread(r);
                        thread.start();
                        try {
                            thread.join();
                        } catch (InterruptedException e) {
                            System.out.println("COULDN'T DO JOIN");
                            e.printStackTrace();
                        }
                    }
                }
            }
            if (multiThreading) {
                es.shutdown();
                try {
                    es.awaitTermination(10, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    System.out.println("ERROR: Couldnt await termination¿?"); //TODO improve message
                    e.printStackTrace();
                }
            }
            BenchmarkManager.finishBenchmark(2);
        }
        mergeTmpOutput();
    }

    /**
     * Divides every PDF in INPUT_PATH into equal parts of 10pages and stores it in a temporary directory
     * , due to implementation, this is necessary.
     */
    private static void prepareInput() {
        BenchmarkManager.startBenchmark(1, "PrepareInput");
        File dir = new File(INPUT_PATH);
        File tmpDir = new File(TMP_INPUT_PATH);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            for (File child : directoryListing) {
                if (child.getName().endsWith(".pdf")) {
                    PDDocument doc = null;
                    try {
                        doc = PDDocument.load(child);
                    } catch (IOException e) {
                        System.out.println("Couldn't load pdf from " + child.getName());
                        e.printStackTrace();
                    }
                    if (doc != null) {
                        List<PDDocument> pdfInParts = splitPdf(doc, 10, child.getName());
                        if (pdfInParts != null) {
                            saveDocList(pdfInParts, child.getName(), tmpDir, doc);
                        }
                    }
                }
            }
        }
        BenchmarkManager.finishBenchmark(1);
    }

    /**
     * Splits a given pdf into parts with pages ranging from 0 to maxPages
     *
     * @param maxPagesPerDoc Max pages of a single doc, past this number would generate another pdf
     * @return list of pdf docs generated
     */
    private static List<PDDocument> splitPdf(PDDocument doc, int maxPagesPerDoc, String name) {

        List<PDDocument> pages;
        try {
            Splitter splitter = new Splitter();
            splitter.setSplitAtPage(maxPagesPerDoc);
            pages = splitter.split(doc);
        } catch (IOException e) {
            //FIXME HACER ALGO AQUI
            System.out.println("ERROR: NO SE PUDO REALIZAR EL SPLIT " + name);
            e.printStackTrace();
            return null;
        }
        return pages;
    }

    /**
     * Stores all docs received with a pattern of 3 numbers with leading zeros
     *
     * @param docList    List of PDDocument
     * @param docName    General name before 3 numbers
     * @param outputPath Path where docs will be saved
     */
    private static void saveDocList(List<PDDocument> docList, String docName, File outputPath, PDDocument originalDoc) {
        Iterator<PDDocument> it = docList.listIterator();
        int i = 1;
        while (it.hasNext()) {
            String outputFilePath = outputPath
                    + "\\"
                    + docName.substring(0, docName.length() - 4)
                    + "_" + String.format("%03d", i) + ".pdf";
            try {
                PDDocument doc = it.next();
                doc.save(outputFilePath);
                doc.close();

            } catch (IOException e) {
                System.out.println("ERROR: COULDNT SAVE PDF " + outputFilePath);
                e.printStackTrace();
            }
            i++;
        }
        try {
            originalDoc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

//    /**
//     * Turns each line in TEXT_TO_REMOVE_PATH into a string to apply rules later
//     *
//     * @return List containing each line of text
//     */
//    private static List<String> extractTextRules() {
//        Scanner sc = null;
//        try {
//            sc = new Scanner(new File(TEXT_TO_REMOVE_PATH));
//        } catch (FileNotFoundException e) {
//            System.out.println("ERROR: No pudo leer las reglas de texto");
//            e.printStackTrace();
//            return null;
//        }
//        List<String> lines = new ArrayList<String>();
//        while (sc.hasNextLine()) {
//            lines.add(sc.nextLine());
//        }
//        return lines;
//    }

    /**
     * Adds each line in path into a List<String>
     *
     * @return List containing each line of text
     */
    private static List<String> linesToList(String path) {
        Scanner sc = null;
        try {
            sc = new Scanner(new File(path));
        } catch (FileNotFoundException e) {
            System.out.println("ERROR: No pudo leer las reglas de texto");
            e.printStackTrace();
            return null;
        }
        List<String> lines = new ArrayList<String>();
        while (sc.hasNextLine()) {
            lines.add(sc.nextLine());
        }
        return lines;
    }

    /**
     * Merges all pages in TMP_OUTPUT from 000 to 999 according to their file names.
     */
    private static void mergeTmpOutput() {
        BenchmarkManager.startBenchmark(3, "MergeOutput");
        File dir = new File(TMP_OUTPUT_PATH);
        File[] directoryListing = dir.listFiles();
        if (directoryListing != null) {
            String prevDestName = "";
            PDFMergerUtility merger = new PDFMergerUtility();
            for (File child : directoryListing) {
                String name = child.getName();
                if (name.endsWith(".pdf")) {//TODO maybe quitar
                    //Removes tmp numbers and tmp directory from final path, adds silly signature at the beginning ;D
                    String destName = (OUTPUT_PATH + "\\" + name.substring(0, name.lastIndexOf('_')) + ".pdf")
                            .replace("wuolah-free-", "");
                    //First iteration
                    if (prevDestName.equals("")) {
                        merger.setDestinationFileName(destName);
                        prevDestName = destName;
                    }
                    //Still same final PDF file
                    if (destName.equals(prevDestName)) {
                        mergerAddSource(merger, child);
                        //PDF file changed, so time to merge last file.
                    } else {
                        mergeDocs(merger);
                        merger = new PDFMergerUtility();
                        merger.setDestinationFileName(destName);
                        mergerAddSource(merger, child);
                    }
                    prevDestName = destName;
                }
            }
            mergeDocs(merger);
        }
        BenchmarkManager.finishBenchmark(3);
    }

    /**
     * Adds a pdf doc to the merger to be appended later on
     *
     * @param merger Merger object
     * @param file   File to append to merger
     */
    private static void mergerAddSource(PDFMergerUtility merger, File file) {
        try {
            merger.addSource(file);
        } catch (FileNotFoundException e) {
            System.out.println("No se pudo añadir " + file.getName() + " al merge");
            e.printStackTrace();
        }
    }


    /**
     * Merge all pages in merger
     *
     * @param merger contains the pdf pages de merge
     */
    private static void mergeDocs(PDFMergerUtility merger) {
        try {
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
        } catch (IOException e) {
            System.out.println("No se pudo hacer el merge de " + merger.getDestinationFileName());
            e.printStackTrace();
        }
        System.out.println(merger.getDestinationFileName() + "Merged succesfully");
    }

    /**
     * Deletes directory and their contents recursively.
     *
     * @param directoryToBeDeleted Directory to delete
     * @return whether or not the deletion was successful.
     */
    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    /**
     * Deletes both tmp directories and their contents
     */
    private static void cleanTmp() {
        deleteDirectory(new File(TMP_INPUT_PATH));
        deleteDirectory(new File(TMP_OUTPUT_PATH));
    }

    /**
     * Lets select image from page to download, only intended for developing/debugging.
     */
    private static void downloadImageFromPage(String pdfPath, int pageIndex) {
        PdfDocument doc = new PdfDocument();
        doc.loadFromFile(pdfPath);
        PdfPageBase page = doc.getPages().get(pageIndex);

        PdfImageInfo[] images = page.getImagesInfo();
        System.out.println("Images in this page: ");
        for (PdfImageInfo image : images) {
            System.out.println("Index: " + image.getIndex() + ".\t Bounds: " + image.getBounds());
        }
        Scanner scanner = new Scanner(System.in);
        int index = -1;
        while (index == -1) {
            System.out.println("Enter index of image: ");
            String indexString = scanner.nextLine();
            try {
                index = Integer.parseInt(indexString);
            } catch (NumberFormatException e) {
                System.out.println("Must be a number");
                e.printStackTrace();
            }
            if (index < 0 || index > images.length - 1) {
                index = -1;
                System.out.println("Index must be > 0 and < " + (images.length - 1));
            }
        }
        File outputfile = new File("image.jpg");
        try {
            ImageIO.write(images[index].getImage(), "jpg", outputfile);
        } catch (IOException e) {
            System.out.println("ERROR: COULDN'T SAVE IMAGE");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        downloadImageFromPage(INPUT_PATH + "\\wuolah-free-LFAyC-EVAL2-2015-2016.pdf", 0);
        checkPaths();
        analyzeInput(true);
        cleanTmp();
        BenchmarkManager.showBenchmarkLog();
    }
}
