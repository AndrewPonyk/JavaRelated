package com.ap;

import org.glassfish.jersey.media.multipart.*;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.*;

@Path("/helloWorldREST")
public class HelloWorldREST {
    @GET
    @Path("/{parameter}")
    public Response responseMsg(@PathParam("parameter") String parameter,
                                @DefaultValue("Nothing to say") @QueryParam("value") String value) {

        String output = "Hello from: " + parameter + " : " + value;
        return Response.status(200).entity(output).build();
    }

    @GET
    @Path("/{id}/{name}")
    public String msg(@PathParam("id") Integer id, @PathParam("name") String name){
        return "Id:" + id + "-- Name:" + name;
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response uploadFile(@FormDataParam("file")InputStream fileInputStream,
                               @FormDataParam("file") FormDataContentDisposition formDataConnectDispositionHeader){
        String path = "/home/andrii/temp/";
        String filename = formDataConnectDispositionHeader.getFileName();
        saveFile(fileInputStream, path + filename);
        String output = "File uploaded to " + path;
        return Response.status(200).entity(output).build();
    }


    /*Another way to save file Using FormDataMultiPart:
        You can also use the FormDataMultiPart class, that simply represents the HTML form and its parts.
        As you will see it is very convenient when used in a form with a big number of multipart fields.
        Packing them all in one Object means that you don’t have to define a lot of arguments in your method, plus you will be able to handle
        fields with arbitrary names etc. Let’s see how you can use it :
    * */
    @POST
    @Path("/upload2Version")
    public Response uploadFile2Version(FormDataMultiPart form){
        String path = "/home/andrii/temp/";
        FormDataBodyPart filePart = form.getField("file");
        ContentDisposition handleOfFilePart = filePart.getContentDisposition();
        String output = path + handleOfFilePart.getFileName();

        InputStream fileInputStream = filePart.getValueAs(InputStream.class);
        saveFile(fileInputStream, output);


        return Response.status(200).entity(output).build();
    }

    // save uploaded file to a defined location on the server
    private void saveFile(InputStream uploadedInputStream,
                          String serverLocation) {

        try {
            OutputStream outpuStream = new FileOutputStream(new File(serverLocation));
            int read = 0;
            byte[] bytes = new byte[1024];

            outpuStream = new FileOutputStream(new File(serverLocation));
            while ((read = uploadedInputStream.read(bytes)) != -1) {
                outpuStream.write(bytes, 0, read);
            }
            outpuStream.flush();
            outpuStream.close();
        } catch (IOException e) {

            e.printStackTrace();
        }

    }
}
