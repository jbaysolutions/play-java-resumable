package controllers;

import com.jbaysolutions.resumable.HttpUtils;
import com.jbaysolutions.resumable.ResumableInfo;
import com.jbaysolutions.resumable.ResumableInfoStorage;
import play.mvc.*;

import views.html.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        return ok(index.render("Your new application is ready."));
    }

    public Result upload() {
        System.out.println("UPLOAD!");
        try {
            int resumableChunkNumber = getResumableChunkNumber(request());

            ResumableInfo info = getResumableInfo(request());

            RandomAccessFile raf = new RandomAccessFile(info.resumableFilePath, "rw");

            //Seek to position
            raf.seek((resumableChunkNumber - 1) * (long)info.resumableChunkSize);

            //Save to file
            InputStream is = new FileInputStream(request().body().asRaw().asFile());
            long readed = 0;
            long content_length = request().body().asRaw().size();
            byte[] bytes = new byte[1024 * 100];
            while(readed < content_length) {
                int r = is.read(bytes);
                if (r < 0)  {
                    break;
                }
                raf.write(bytes, 0, r);
                readed += r;
            }
            raf.close();
            is.close();


            //Mark as uploaded.
            info.uploadedChunks.add(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber));
            if (info.checkIfUploadFinished()) { //Check if all chunks uploaded, and change filename
                ResumableInfoStorage.getInstance().remove(info);
                return ok("All finished.");
            } else {
                return ok("Upload");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError();
        }
    }

    public Result uploadStatus() {
        try {
            System.out.println("UPLOAD STATUS!");
            int resumableChunkNumber        = getResumableChunkNumber(request());

            ResumableInfo info = getResumableInfo(request());

            if (info.uploadedChunks.contains(new ResumableInfo.ResumableChunkNumber(resumableChunkNumber))) {
                return ok("Uploaded."); //This Chunk has been Uploaded.
            } else {
                return notFound();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError();
        }
    }


    private int getResumableChunkNumber(Http.Request request) {
        return HttpUtils.toInt(request.getQueryString("resumableChunkNumber"), -1);
    }

    private ResumableInfo getResumableInfo(Http.Request request) throws Exception {
//        String base_dir = "C:/TMP/";
        String base_dir = System.getProperty("java.io.tmpdir") + File.pathSeparator + "UPLOAD_DIR/";

        int resumableChunkSize          = HttpUtils.toInt(request.getQueryString("resumableChunkSize"), -1);
        long resumableTotalSize         = HttpUtils.toLong(request.getQueryString("resumableTotalSize"), -1);
        String resumableIdentifier      = request.getQueryString("resumableIdentifier");
        String resumableFilename        = request.getQueryString("resumableFilename");
        String resumableRelativePath    = request.getQueryString("resumableRelativePath");
        //Here we add a ".temp" to every upload file to indicate NON-FINISHED
        new File(base_dir).mkdir();
        String resumableFilePath        = new File(base_dir, resumableFilename).getAbsolutePath() + ".temp";

        ResumableInfoStorage storage = ResumableInfoStorage.getInstance();

        ResumableInfo info = storage.get(resumableChunkSize, resumableTotalSize,
                resumableIdentifier, resumableFilename, resumableRelativePath, resumableFilePath);
        if (!info.vaild())         {
            storage.remove(info);
            throw new Exception("Invalid request params.");
        }
        return info;
    }


}
