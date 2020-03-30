import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class AssetConverter {

    final static String SVG_SUFFIX = "_svg";


    final static String SVG_EXTENSION = ".svg";
    final static String XML_EXTENSION = ".xml";
    final static String WEBP_EXTENSION = ".webp";

    final static String PATH = "<path";

    final static String PNG_EXTENSION = ".png";
    final static String NINE_PATH_PNG_EXTENSION = "9.png";

    final static int COPY_MISSING_FILE_TO_RESOURCE = 1;
    final static int RENAME_AND_COPY_TO_DRAWABLE = 2;
    final static int COPY_NINE_PATCH_TO_DRAWABLE = 21;
    final static int COPY_MISSING_SVG_BY_PNG = 3;
    final static int PRINT_MISSING_FILE = 4;

    final static String RESOURCE_FOLDER = System.getProperty("user.dir") + File.separator + "App_Asset";

    static DrawableFilePath[] drawableFolders = DrawableFilePath.createDrawableFilePaths();

    static int MAX_XML_FILE_SIZE = 20;

    public static void main(String[] args) {
        String resourceFolder = RESOURCE_FOLDER;
        if (containFileWithName(args, "--input-folder")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("--input-folder")) {
                    resourceFolder = args[i + 1];
                    break;
                }
            }
        }
        if (containFileWithName(args, "--max-xml-size")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("--max-xml-size")) {
                    try {
                        MAX_XML_FILE_SIZE = Integer.parseInt(args[i + 1]);
                    } catch (Exception e) {

                    }
                    break;
                }
            }
        }

        System.out.println("\nInput Folder: " + resourceFolder);

        File rootFile = new File(resourceFolder);

        // Clear and Init Folder
        DrawableFilePath.removeFolder();
        DrawableFilePath.createFolder();

        System.out.println("\nCurrent Missing Files");
        iterateFile(rootFile, PRINT_MISSING_FILE);

        // 2. Nine patch file
        System.out.println("\nStart Copy 9 Patch PNG Files to Tmp folder");
        iterateFile(rootFile, COPY_NINE_PATCH_TO_DRAWABLE);

        // 3 Copy missing SVG file to tmp converter folder
        System.out.println("\nStart Copy SVG Files to Tmp folder");
        iterateFile(rootFile, COPY_MISSING_FILE_TO_RESOURCE);

        System.out.println("\nStart convert svg to xml then copy to drawable");
        // 3.1 Convert to xml file
        convertSvgToXml();

        // 3.2 Rename and copy
        File svgConverter = new File(DrawableFilePath.SVG_CONVERT_RESULT_FOLDER_PATH);

        System.out.println("\nXML Change files:");
        iterateFile(svgConverter, RENAME_AND_COPY_TO_DRAWABLE);

        // 4. Handle PNG files
        System.out.println("\nStart Copy missing PNG Files, which can not convert from svg");
        iterateFile(rootFile, COPY_MISSING_SVG_BY_PNG);
        System.out.println("\nPNG, Webp Change files:");
        convertPngToWebp();
        compareWebpThenCopyToDrawable();

        System.out.println("\nFiles Can not copy to drawable:");
        iterateFile(rootFile, PRINT_MISSING_FILE);

        //removeTmpFolder(forceCopy);
    }

    /**
     * SVG
     */
    public static void convertSvgToXml() {
        try {
            Runtime.getRuntime().exec("java -jar Svg2VectorAndroid.jar " + DrawableFilePath.SVG_CONVERT_RESULT_FOLDER_PATH).waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Common
     *
     * @param file
     * @return
     */
    public static boolean isExistInDrawable(File file) {
        for (DrawableFilePath drawableFilePath : drawableFolders) {
            File childExistFile = drawableFilePath.drawableFolder;
            if (childExistFile.list() == null) continue;
            if (containFileWithName(childExistFile.list(), file.getName())) return true;
        }
        return false;
    }

    public static boolean isExistInVectorDrawable(File file) {
        File childExistFile = DrawableFilePath.DEFAULT_DRAWABLE_VECTOR_FILE_PATH.drawableFolder;
        if (childExistFile.list() == null) return false;
        if (containFileWithName(childExistFile.list(), file.getName())) return true;
        return false;
    }

    public static boolean isExistInNinePatch(File file) {
        for (DrawableFilePath drawableFilePath : drawableFolders) {
            File childExistFile = drawableFilePath.drawableFolder;
            if (childExistFile.list() == null) continue;

            for (File childFile : childExistFile.listFiles()) {
                if (childFile.getName().contains(NINE_PATH_PNG_EXTENSION) == false) continue;
                String childName = DrawableFilePath.getSimpleNameWithoutExtension(childFile.getName());
                String fileName = DrawableFilePath.getSimpleNameWithoutExtension(file.getName());
                if (childName.equalsIgnoreCase(fileName)) return true;
            }
        }
        return false;
    }

    /**
     * Iterator folder
     */
    public static void iterateFile(File file, int type) {
        if (!file.exists()) return;

        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                if (f.isDirectory()) {
                    iterateFile(f, type);
                } else {
                    switch (type) {
                        case COPY_MISSING_FILE_TO_RESOURCE:
                            copyMissingSvgToConvertFolder(f);
                            break;
                        case RENAME_AND_COPY_TO_DRAWABLE:
                            renameAndCopyXmlToDrawable(f);
                            break;

                        case COPY_NINE_PATCH_TO_DRAWABLE:
                            copyNinePatchToDrawable(f);
                            break;
                        case COPY_MISSING_SVG_BY_PNG:
                            copyMissingPngToConvertFolder(f);
                            break;
                        case PRINT_MISSING_FILE:
                            printAllMissingFile(f);
                            break;
                    }
                }
            }
        }
    }

    private static void printAllMissingFile(File file) {
        if (!file.exists()) return;

        boolean containSVG = file.getName().contains(SVG_EXTENSION);
        boolean validPNG = file.getName().contains(PNG_EXTENSION) && file.getParent().contains("xxxhdpi");

        if (!containSVG && !validPNG) return;

        if (!isExistInDrawable(file)) {
            System.out.println(file.getName());
        }
    }

    /**
     * COPY_MISSING_FILE_TO_RESOURCE
     */
    public static void copyMissingSvgToConvertFolder(File file) {
        if (!file.exists()) return;
        if (!file.getName().contains(SVG_EXTENSION)) return;
        if (isExistInNinePatch(file)) return;

        try {
            File fileDes = DrawableFilePath.getSvgConverterFolder(file);
            if (!fileDes.exists()) fileDes.createNewFile();
            Files.copy(file.toPath(), fileDes.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Copy 9 patch file to drawable xxxhdpi
     *
     * @param file to copy
     */
    public static void copyNinePatchToDrawable(File file) {
        if (!file.exists()) return;
        if (!file.getName().contains(NINE_PATH_PNG_EXTENSION)) return;
        if (!DrawableFilePath.isValidPng(file)) return;
        if (isExistInVectorDrawable(file)) return;

        String newName = DrawableFilePath.getDrawableFile(file);
        if (newName == null) System.out.println("File is Invalid: " + file.getAbsolutePath());
        File fileNew = new File(newName);

        if (file.length() != fileNew.length()) {
            System.out.println(fileNew.getName());
        }

        try {
            if (!fileNew.exists()) fileNew.createNewFile();
            Files.copy(file.toPath(), fileNew.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * RENAME_AND_COPY_TO_DRAWABLE xml to drawable
     */
    public static void renameAndCopyXmlToDrawable(File file) {
        try {
            if (!file.exists()) return;
            if (!file.getName().contains(SVG_SUFFIX)) return;
            if (!file.getName().contains(XML_EXTENSION)) return;
            if (isNotValidXmlFile(file)) return;
            if (file.length() / 1024 >= MAX_XML_FILE_SIZE) return;
            if (isExistInNinePatch(file)) return;

            removeAlpha(file);

            String newName = file.getName().replace(SVG_SUFFIX, "").toLowerCase();
            File fileNew = new File(DrawableFilePath.PNG_DRAWABLE_FOLDER + File.separator + newName);

            boolean exist = isExistInDrawable(fileNew);
            if (exist) return;

            if (file.length() != fileNew.length()) {
                System.out.println(fileNew.getName());
            }
            file.renameTo(fileNew);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * SVG
     *
     * @param file
     * @return
     */
    public static boolean isNotValidXmlFile(File file) {
        if (!file.exists()) return true;

        Boolean containPath = false;
        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String st;
            while ((st = br.readLine()) != null) {
                if (st.contains(PATH)) {
                    containPath = true;
                }
            }
            br.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return !containPath;
    }

    /**
     * PNG_WEBP
     */
    public static void copyMissingPngToConvertFolder(File file) {
        if (!file.exists()) return;
        if (!file.getName().contains(PNG_EXTENSION)) return;
        if (!DrawableFilePath.isValidPng(file)) return;
        if (isExistInVectorDrawable(file)) return;
        if (isExistInNinePatch(file)) return;

        String des = DrawableFilePath.getPngConverterFile(file);
        if (des == null) System.out.println("File is invalid: " + file.getAbsolutePath());

        try {
            File fileDes = new File(des);
            if (!fileDes.exists()) fileDes.createNewFile();
            Files.copy(file.toPath(), fileDes.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * PNG_WEBP
     */
    public static void convertPngToWebp() {
        for (DrawableFilePath drawableFilePath : drawableFolders) {
            File folder = drawableFilePath.converterFolder;

            if (folder.listFiles() == null) continue;
            for (File file : folder.listFiles()) {
                if (file.getName().contains(PNG_EXTENSION)) {
                    String filePath = DrawableFilePath.getPngConverterFile(file);
                    String fileWebpPath = DrawableFilePath.getSimpleNameWithoutExtension(filePath) + WEBP_EXTENSION;
                    String bash = "./cwebp -q 80 " + filePath + " -o " + fileWebpPath;
                    try {
                        Runtime.getRuntime().exec(bash).waitFor();
                    } catch (InterruptedException | IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * PNG_WEBP
     */
    public static void compareWebpThenCopyToDrawable() {
        File converterXXXHdpiFolder = DrawableFilePath.DEFAULT_DRAWABLE_FILE_PATH.converterFolder;

        if (converterXXXHdpiFolder.listFiles() == null) return;
        for (File pngFile : converterXXXHdpiFolder.listFiles()) {
            if (pngFile.getName().contains(PNG_EXTENSION)) {
                String fileWebpPath = DrawableFilePath.getSimpleNameWithoutExtension(pngFile.getAbsolutePath()) + WEBP_EXTENSION;
                File webpFile = new File(fileWebpPath);

                if (isExistInVectorDrawable(pngFile) || isExistInNinePatch(pngFile)) continue;
                if (pngFile.length() > webpFile.length() && webpFile.length() != 0) {
                    copyPngWebpToDrawable(webpFile.getName());
                } else {
                    copyPngWebpToDrawable(pngFile.getName());
                }
            }
        }
    }

    /**
     * PNG_WEBP
     *
     * @param fileName
     */
    public static void copyPngWebpToDrawable(String fileName) {
        for (DrawableFilePath drawableFilePath : drawableFolders) {
            if (drawableFilePath.prefix.equals("")) continue;
            File originFile = new File(drawableFilePath.converterFolder.getAbsolutePath() + File.separator + fileName);
            File drawableFile = new File(drawableFilePath.drawableFolder.getAbsolutePath() + File.separator + fileName);

            if (!originFile.exists()) continue;
            try {
                if (!drawableFile.exists()) drawableFile.createNewFile();
                Files.copy(originFile.toPath(), drawableFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public static boolean containFileWithName(String[] list, String value) {
        for (String item : list) {
            String nameFirst = DrawableFilePath.getSimpleNameWithoutExtension(item);
            String nameSecond = DrawableFilePath.getSimpleNameWithoutExtension(value);
            if (nameFirst.equalsIgnoreCase(nameSecond)) return true;
        }
        return false;
    }


    final static String TARGET_DIR = "removeAlpha";
    final static String ALPHA_TEXT = "android:fillAlpha";
    final static String IC_24SYSTEM = "ic_24system";
    final static String IC_40SYSTEM = "ic_40system";
    final static String IC_16INLINE = "ic_16inline";

    public static void removeAlpha(File file) {
        if (!file.exists()) return;
        if (!file.getName().contains(XML_EXTENSION)) return;
        if (!file.getName().contains(IC_24SYSTEM) && !file.getName().contains(IC_40SYSTEM) && !file.getName().contains(IC_16INLINE))
            return;
        if (file.getName().contains("ink2") || file.getName().contains("ink3") || file.getName().contains("ink4"))
            return;

        File newDir = new File(file.getParentFile().getAbsolutePath().toLowerCase() + "/" + TARGET_DIR);
        if (!newDir.exists()) newDir.mkdirs();

        String newFilePath = file.getParentFile().getAbsolutePath().toLowerCase() + "/" + TARGET_DIR + "/" + file.getName().toLowerCase();
        File newFile = new File(newFilePath);
        if (!file.exists())
            try {
                newFile.createNewFile();
            } catch (IOException e1) {
                e1.printStackTrace();
            }

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            PrintWriter pr = new PrintWriter(newFilePath);
            String st;
            while ((st = br.readLine()) != null) {
                if (!st.contains(ALPHA_TEXT)) {
                    pr.println(st);
                }
            }
            br.close();
            pr.close();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        try {
            if (file.exists() == false) file.createNewFile();
            Files.copy(newFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Manage resources folders
     */
    static class DrawableFilePath {
        final static String PREFIX_XXXHDPI = "xxxhdpi";
        final static String PREFIX_XXHDPI = "xxhdpi";
        final static String PREFIX_XHDPI = "xhdpi";
        final static String PREFIX_HDPI = "hdpi";

        final static String SVG_CONVERT_RESULT_FOLDER_PATH = System.getProperty("user.dir") + File.separator + "drawable";
        final static String PNG_CONVERT_RESULT_FOLDER_PATH_HDPI = System.getProperty("user.dir") + File.separator + "drawable-hdpi";
        final static String PNG_CONVERT_RESULT_FOLDER_PATH_XHDPI = System.getProperty("user.dir") + File.separator + "drawable-xhdpi";
        final static String PNG_CONVERT_RESULT_FOLDER_PATH_XXHDPI = System.getProperty("user.dir") + File.separator + "drawable-xxhdpi";
        final static String PNG_CONVERT_RESULT_FOLDER_PATH_XXXHDPI = System.getProperty("user.dir") + File.separator + "drawable-xxxhdpi";

        final static String PNG_DRAWABLE_FOLDER = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable";
        final static String PNG_DRAWABLE_FOLDER_HDPI = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable-hdpi";
        final static String PNG_DRAWABLE_FOLDER_XHDPI = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable-xhdpi";
        final static String PNG_DRAWABLE_FOLDER_XXHDPI = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable-xxhdpi";
        final static String PNG_DRAWABLE_FOLDER_XXXHDPI = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable-xxxhdpi";

        String prefix;
        File converterFolder;
        File drawableFolder;

        public DrawableFilePath(String prefix, String converterPath, String drawablePath) {
            this.prefix = prefix;
            this.converterFolder = new File(converterPath);
            this.drawableFolder = new File(drawablePath);
        }

        public static DrawableFilePath[] createDrawableFilePaths() {
            DrawableFilePath[] drawableFolders = new DrawableFilePath[5];
            drawableFolders[0] = new DrawableFilePath("", SVG_CONVERT_RESULT_FOLDER_PATH, PNG_DRAWABLE_FOLDER);
            drawableFolders[1] = new DrawableFilePath(PREFIX_HDPI, PNG_CONVERT_RESULT_FOLDER_PATH_HDPI, PNG_DRAWABLE_FOLDER_HDPI);
            drawableFolders[2] = new DrawableFilePath(PREFIX_XHDPI, PNG_CONVERT_RESULT_FOLDER_PATH_XHDPI, PNG_DRAWABLE_FOLDER_XHDPI);
            drawableFolders[3] = new DrawableFilePath(PREFIX_XXHDPI, PNG_CONVERT_RESULT_FOLDER_PATH_XXHDPI, PNG_DRAWABLE_FOLDER_XXHDPI);
            drawableFolders[4] = new DrawableFilePath(PREFIX_XXXHDPI, PNG_CONVERT_RESULT_FOLDER_PATH_XXXHDPI, PNG_DRAWABLE_FOLDER_XXXHDPI);
            return drawableFolders;
        }

        final static DrawableFilePath DEFAULT_DRAWABLE_FILE_PATH = new DrawableFilePath(PREFIX_XXXHDPI, PNG_CONVERT_RESULT_FOLDER_PATH_XXXHDPI, PNG_DRAWABLE_FOLDER_XXXHDPI);
        final static DrawableFilePath DEFAULT_DRAWABLE_VECTOR_FILE_PATH = new DrawableFilePath("", SVG_CONVERT_RESULT_FOLDER_PATH, PNG_DRAWABLE_FOLDER);

        public static String getDrawableFile(File file) {
            String parent = file.getParent();
            if (parent.contains(PREFIX_XXXHDPI)) return PNG_DRAWABLE_FOLDER_XXXHDPI + File.separator + file.getName();
            if (parent.contains(PREFIX_XXHDPI)) return PNG_DRAWABLE_FOLDER_XXHDPI + File.separator + file.getName();
            if (parent.contains(PREFIX_XHDPI)) return PNG_DRAWABLE_FOLDER_XHDPI + File.separator + file.getName();
            if (parent.contains(PREFIX_HDPI)) return PNG_DRAWABLE_FOLDER_HDPI + File.separator + file.getName();
            return null;
        }

        public static String getPngConverterFile(File file) {
            String parent = file.getParent();
            if (parent.contains(PREFIX_XXXHDPI))
                return PNG_CONVERT_RESULT_FOLDER_PATH_XXXHDPI + File.separator + file.getName();
            if (parent.contains(PREFIX_XXHDPI))
                return PNG_CONVERT_RESULT_FOLDER_PATH_XXHDPI + File.separator + file.getName();
            if (parent.contains(PREFIX_XHDPI))
                return PNG_CONVERT_RESULT_FOLDER_PATH_XHDPI + File.separator + file.getName();
            if (parent.contains(PREFIX_HDPI))
                return PNG_CONVERT_RESULT_FOLDER_PATH_HDPI + File.separator + file.getName();
            return null;
        }

        public static void removeFolder() {
            try {
                Runtime.getRuntime().exec("rm -rf " + SVG_CONVERT_RESULT_FOLDER_PATH).waitFor();
                Runtime.getRuntime().exec("rm -rf " + PNG_CONVERT_RESULT_FOLDER_PATH_XXXHDPI).waitFor();
                Runtime.getRuntime().exec("rm -rf " + PNG_CONVERT_RESULT_FOLDER_PATH_HDPI).waitFor();
                Runtime.getRuntime().exec("rm -rf " + PNG_CONVERT_RESULT_FOLDER_PATH_XXHDPI).waitFor();
                Runtime.getRuntime().exec("rm -rf " + PNG_CONVERT_RESULT_FOLDER_PATH_XHDPI).waitFor();

            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

        private static void createFolder() {
            try {
                Runtime.getRuntime().exec("mkdir asset").waitFor();
                Runtime.getRuntime().exec("mkdir asset/src").waitFor();
                Runtime.getRuntime().exec("mkdir asset/src/main").waitFor();
                Runtime.getRuntime().exec("mkdir asset/src/main/res").waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_DRAWABLE_FOLDER).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_DRAWABLE_FOLDER_XXXHDPI).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_DRAWABLE_FOLDER_XXHDPI).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_DRAWABLE_FOLDER_XHDPI).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_DRAWABLE_FOLDER_HDPI).waitFor();

                Runtime.getRuntime().exec("mkdir " + SVG_CONVERT_RESULT_FOLDER_PATH).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_CONVERT_RESULT_FOLDER_PATH_XXXHDPI).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_CONVERT_RESULT_FOLDER_PATH_XXHDPI).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_CONVERT_RESULT_FOLDER_PATH_XHDPI).waitFor();
                Runtime.getRuntime().exec("mkdir " + PNG_CONVERT_RESULT_FOLDER_PATH_HDPI).waitFor();
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

        public static File getSvgConverterFolder(File file) {
            return new File(SVG_CONVERT_RESULT_FOLDER_PATH + File.separator + file.getName().toLowerCase());
        }

        public static String getSimpleNameWithoutExtension(String name) {
            int childExtIndex = name.indexOf(".");
            if (childExtIndex == -1) return name;
            return name.substring(0, childExtIndex).toLowerCase();
        }

        public static boolean isValidPng(File file) {
            if (file.getName().contains("@2x")) return false;
            if (file.getName().contains("@3x")) return false;
            if (file.getParent().contains(PREFIX_XXXHDPI)) return true;
            if (file.getParent().contains(PREFIX_XXHDPI)) return true;
            if (file.getParent().contains(PREFIX_XHDPI)) return true;
            if (file.getParent().contains(PREFIX_HDPI)) return true;
            return false;
        }
    }
}
