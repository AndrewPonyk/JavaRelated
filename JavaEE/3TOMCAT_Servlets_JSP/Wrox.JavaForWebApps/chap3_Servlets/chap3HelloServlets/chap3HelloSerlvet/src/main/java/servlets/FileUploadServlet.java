package servlets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebServlet(urlPatterns = "/fileUpload")
@MultipartConfig()
public class FileUploadServlet extends HttpServlet{
	
	final String PATH_TO_SAVE = this.getClass().getClassLoader().getResource("").
			getPath().split("classes")[0] + "uploadedFiles/";
	
	private static final long serialVersionUID = 1L;

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		final Part part = req.getPart("file");
		PrintWriter writer = resp.getWriter();
		final String fileName = getFileName(part);

		
		
		OutputStream out = null;
		InputStream filecontent = null;
		writer.println(part);
		System.out.println(part);
		try{
			out = new FileOutputStream(new File(PATH_TO_SAVE + fileName));
			
			filecontent = part.getInputStream();
			
			int read = 0;
			final byte[] bytes = new byte[1024];
			while ((read = filecontent.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
			writer.append("Files is saved to path" + PATH_TO_SAVE + fileName);
		}catch(Exception e){
			e.printStackTrace();
		}
		finally {
			if (out != null) {
				out.close();
			}
			if (filecontent != null) {
				filecontent.close();
			}
			if (writer != null) {
				writer.close();
			}
		}
	}
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		/// WITH THIS Ukrainian letters works
		resp.setHeader("Content-Type", "text/html; charset=UTF-8");
		
		PrintWriter writer = resp.getWriter();
	
		writer.append("<form action='fileUpload' method='post'").
		append("enctype='multipart/form-data'>").
		append("<input type='file' name='file' size='50' />").
        append("<br />"). 
		append("<input type='submit' value='Upload File' />").
		append("</form>");
		
	}
	
	private String getFileName(final Part part) {
		final String partHeader = part.getHeader("content-disposition");
		//LOGGER.log(Level.INFO, "Part Header = {0}", partHeader);
		for (String content : part.getHeader("content-disposition").split(";")) {
			if (content.trim().startsWith("filename")) {
				return content.substring(content.indexOf('=') + 1).trim()
						.replace("\"", "");
			}
		}
		return null;
	}
	
}
