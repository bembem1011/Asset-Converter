import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class AssetConverter {

    final static String SVG_SUFFIX = "_svg";
    final static String SVG_CONVERT_RESULT_FOLDER_PATH = System.getProperty("user.dir") + File.separator + "svg_converter";
    final static String PNG_CONVERT_RESULT_FOLDER_PATH = System.getProperty("user.dir") + File.separator + "png_converter";

    final static String SVG_EXTENSION = ".svg";
    final static String XML_EXTENSION = ".xml";

    final static String PATH = "<path";
    final static String DRAWABLE_FOLDER = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable";
    final static String PNG_DRAWABLE_FOLDER = System.getProperty("user.dir") + File.separator + "asset/src/main/res/drawable-xxxhdpi";

    final static String PNG_EXTENSION = ".png";
    final static String NINE_PATH_PNG_EXTENSION = ".png";

    final static int COPY_MISSING_FILE_TO_RESOURCE = 1;
    final static int RENAME_AND_COPY_TO_DRAWABLE = 2;
    final static int COPY_MISSING_SVG_BY_PNG = 3;
    final static int PRINT_MISSING_FILE = 4;

    final static String RESOURCE_FOLDER = System.getProperty("user.dir") + File.separator + "App_Asset";

    static File[] rootExistFile = new File[2];

    static boolean FORCE_COPY = false;

    static {
        rootExistFile[0] = new File(DRAWABLE_FOLDER);
        rootExistFile[1] = new File(PNG_DRAWABLE_FOLDER);
    }

    public static void main(String[] args) {
        removeTmpFolder();

        String resourceFolder = RESOURCE_FOLDER;
        if (containFileWithName(args, "--input-folder")) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equalsIgnoreCase("--input-folder")) {
                    resourceFolder = args[i + 1];
                    break;
                }
            }
        }
        FORCE_COPY = containFileWithName(args, "--force-copy");
        System.out.println("\nForce Copy: " + FORCE_COPY);
        System.out.println("\nInput Folder: " + resourceFolder);

        File rootFile = new File(resourceFolder);

        initFolder();

        System.out.println("\nCurrent Missing Files");
        iterateFile(rootFile, PRINT_MISSING_FILE);

        // Copy missing file to tmp converter folder
        System.out.println("\nStart Copy SVG Files to Tmp folder");
        iterateFile(rootFile, COPY_MISSING_FILE_TO_RESOURCE);

        System.out.println("\nStart convert svg to xml then copy to drawable");
        // Convert to xml file
        try {
            Runtime.getRuntime().exec("java -jar Svg2VectorAndroid.jar svg_converter").waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }

        // Rename and copy
        File svgConverter = new File(SVG_CONVERT_RESULT_FOLDER_PATH);

        System.out.println("\nXML Change files:");
        iterateFile(svgConverter, RENAME_AND_COPY_TO_DRAWABLE);

        // Handle PNG files
        System.out.println("\nStart Copy missing PNG Files, which can not convert from svg");
        iterateFile(rootFile, COPY_MISSING_SVG_BY_PNG);
        System.out.println("\nPNG, Webp Change files:");
        cloneAndCompareWebpThenCopyToDrawable(PNG_CONVERT_RESULT_FOLDER_PATH);

        System.out.println("\nFiles Can not copy to drawable:");
        iterateFile(rootFile, PRINT_MISSING_FILE);

        removeTmpFolder();
    }

    private static void initFolder() {
        try {
            File assetFile = new File("asset");
            if (assetFile.exists()) return;
            Runtime.getRuntime().exec("mkdir asset").waitFor();
            Runtime.getRuntime().exec("mkdir asset/src").waitFor();
            Runtime.getRuntime().exec("mkdir asset/src/main").waitFor();
            Runtime.getRuntime().exec("mkdir asset/src/main/res").waitFor();
            Runtime.getRuntime().exec("mkdir asset/src/main/res/drawable").waitFor();
            Runtime.getRuntime().exec("mkdir asset/src/main/res/drawable-xxxhdpi").waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean isExistInDrawable(File file) {
        int fileExtIndex = file.getName().lastIndexOf(".");
        if (fileExtIndex == -1) return false;

        for (File childExistFile : rootExistFile) {
            if (containFileWithName(childExistFile.list(), file.getName())) return true;
        }
        return false;
    }

    public static void removeTmpFolder() {
        try {
            Runtime.getRuntime().exec("rm -rf " + SVG_CONVERT_RESULT_FOLDER_PATH).waitFor();
            Runtime.getRuntime().exec("rm -rf " + PNG_CONVERT_RESULT_FOLDER_PATH).waitFor();
            // Runtime.getRuntime().exec("rm -rf png_converter").waitFor();
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
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
                            copyMissingResoureToConvertFolder(f);
                            break;
                        case RENAME_AND_COPY_TO_DRAWABLE:
                            File fileNew = new File(DRAWABLE_FOLDER);
                            if (!fileNew.exists()) fileNew.mkdirs();
                            renameAndCopyToDrawable(f);
                            break;
                        case COPY_MISSING_SVG_BY_PNG:
                            File filePng = new File(PNG_DRAWABLE_FOLDER);
                            if (!filePng.exists()) filePng.mkdirs();
                            copyMissingPNGResoureToConvertFolder(f);
                            break;
                        case PRINT_MISSING_FILE:
                            printMissingFile(f);
                            break;
                    }
                }
            }
        }
    }

    private static void printMissingFile(File file) {
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
     *
     * @param file
     */
    public static void copyMissingResoureToConvertFolder(File file) {
        if (!file.exists()) return;
        if (!file.getName().contains(SVG_EXTENSION)) return;

        boolean isExist = isExistInDrawable(file);

        if (!isExist || FORCE_COPY) {
            File desFolder = new File(SVG_CONVERT_RESULT_FOLDER_PATH);
            if (!desFolder.exists()) desFolder.mkdirs();
            String des = SVG_CONVERT_RESULT_FOLDER_PATH + File.separator + file.getName().toLowerCase();

            try {
                File fileDes = new File(des);
                if (!fileDes.exists()) fileDes.createNewFile();
                Files.copy(file.toPath(), fileDes.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * RENAME_AND_COPY_TO_DRAWABLE
     *
     * @param file
     */
    public static void renameAndCopyToDrawable(File file) {
        try {
            if (!file.exists()) return;
            if (!file.getName().contains(SVG_SUFFIX)) return;
            if (!file.getName().contains(XML_EXTENSION)) return;
            if (checkFileNotContainPath(file)) return;

            removeAlpha(file);

            String newName = file.getName().replace(SVG_SUFFIX, "").toLowerCase();
            File fileNew = new File(DRAWABLE_FOLDER + File.separator + newName);

            File drawableFolder = new File(DRAWABLE_FOLDER);

            boolean exist = isExistInDrawable(fileNew);
            if (!FORCE_COPY && exist) return;

            if (file.length() != fileNew.length()) {
                System.out.println(fileNew.getName());
            }
            file.renameTo(fileNew);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    // RENAME_AND_COPY_TO_DRAWABLE
    public static Boolean checkFileNotContainPath(File file) {
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
     * COPY_PNG
     *
     * @param file
     */
    public static void copyMissingPNGResoureToConvertFolder(File file) {
        if (!file.exists()) return;
        if (!file.getName().contains(PNG_EXTENSION)) return;
        if (!file.getParent().contains("xxxhdpi")) return;


        if (!isExistInDrawable(file)) {
            File desFolder = new File(PNG_CONVERT_RESULT_FOLDER_PATH);
            if (desFolder.exists() == false) desFolder.mkdirs();
            String des = PNG_CONVERT_RESULT_FOLDER_PATH + File.separator + file.getName().toLowerCase();

            try {
                File fileDes = new File(des);
                if (!fileDes.exists()) fileDes.createNewFile();
                Files.copy(file.toPath(), fileDes.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void cloneAndCompareWebpThenCopyToDrawable(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists()) return;
        if (folder.listFiles() == null) return;
        for (File file : folder.listFiles()) {
            if (file.getName().contains(PNG_EXTENSION)) {
                String filePath = PNG_CONVERT_RESULT_FOLDER_PATH + File.separator + file.getName();
                int fileExtIndex = filePath.lastIndexOf(".");
                String fileWebpPath = filePath.substring(0, fileExtIndex).toLowerCase() + ".webp";
                String bash = "./cwebp -q 75 " + filePath + " -o " + fileWebpPath;
                try {
                    Runtime.getRuntime().exec(bash).waitFor();
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

        for (File file : folder.listFiles()) {
            if (file.getName().contains(PNG_EXTENSION)) {
                int fileExtIndex = file.getAbsolutePath().lastIndexOf(".");
                String fileWebpPath = file.getAbsolutePath().substring(0, fileExtIndex).toLowerCase() + ".webp";
                File webpFile = new File(fileWebpPath);

                if (isExistInDrawable(file) && !FORCE_COPY) continue;
                if (file.length() > webpFile.length()) {
                    String newName = webpFile.getName().toLowerCase();
                    File fileNew = new File(PNG_DRAWABLE_FOLDER + File.separator + newName);
                    webpFile.renameTo(fileNew);

                    if (webpFile.length() != fileNew.length()) {
                        System.out.println(fileNew.getName());
                    }
                } else {
                    String newName = file.getName().toLowerCase();
                    File fileNew = new File(PNG_DRAWABLE_FOLDER + File.separator + newName);
                    file.renameTo(fileNew);
                    if (file.length() != fileNew.length()) {
                        System.out.println(fileNew.getName());
                    }
                }
            }
        }
    }

    public static boolean containFileWithName(String[] list, String value) {
        for (String item : list) {
            String nameFirst = getSimpleNameWithoutExtension(item);
            String nameSecond = getSimpleNameWithoutExtension(value);
            if (nameFirst.equalsIgnoreCase(nameSecond)) return true;
        }
        return false;
    }


    public static String getSimpleNameWithoutExtension(String name) {
        int childExtIndex = name.lastIndexOf(".");
        if (childExtIndex == -1) return name;
        return name.substring(0, childExtIndex).toLowerCase();
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
            Files.copy(newFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
