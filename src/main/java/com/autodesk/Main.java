package com.autodesk;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    //note that this workitem does not specify the "Resource" property for its "Result" output argument
    //this causes AutoCAD.IO to use its own storage. If you want to store the output on your own storage then
    //provide a valid URL for the "Resource" property.
    private static final String WORK_ITEM = "{\n" +
            "    \"odata.type\": \"ACES.Models.WorkItem\",\n" +
            "    \"ActivityId\": { \"odata.type\": \"ACES.Models.EntityId\", \"Id\": \"PlotToPDF\", \"UserId\": \"Shared\" },\n" +
            "    \"Arguments\": {\n" +
            "        \"odata.type\": \"ACES.Models.Arguments\",\n" +
            "        \"InputArguments@odata.type\": \"Collection(ACES.Models.Argument)\",\n" +
            "        \"InputArguments\": [\n" +
            "            {\n" +
            "                \"Name\": \"HostDwg\",\n" +
            "                \"Resource\": \"http://download.autodesk.com/us/samplefiles/acad/blocks_and_tables_-_imperial.dwg\"\n" +
            "            }\n" +
            "        ],\n" +
            "        \"OutputArguments@odata.type\": \"Collection(ACES.Models.Argument)\",\n" +
            "        \"OutputArguments\": [\n" +
            "            {\n" +
            "                \"HttpVerb\": \"POST\",\n" +
            "                \"Name\": \"Result\",\n" +
            "                \"StorageProvider\": \"Generic\"\n" +
            "            }\n" +
            "        ]\n" +
            "    },\n" +
            "    \"Version\": 1\n" +
            "}";

    public static void main(String[] args) throws IOException, ParseException, InterruptedException {
        final String token = getToken("you consumer key", "you consumer secret");
        System.out.print("Submitting workitem...");
        final WorkItemId workItemId = submitWorkItem(token, WORK_ITEM);
        System.out.println("Ok.");
        //this is a console app so we must poll. In a server we could also receive a callback by
        //specifying the "Status" pseudo output argument with a Resource attribute pointing to our
        //callback url
        String status;
        do {
            System.out.println("Sleeping for 2s...");
            Thread.sleep(2000);
            System.out.print("Checking work item status=");
            status = pollWorkItem(token, workItemId);
            System.out.println(status);
        } while (status.compareTo("Pending")==0 || status.compareTo("InProgress")==0);
        if (status.compareTo("Succeeded")==0)
            downloadResults(token, workItemId);
    }

    //obtain authorization token
    static String getToken(final String consumerKey, final String consumerSecret) throws IOException, ParseException {
        final String url = "https://developer.api.autodesk.com/authentication/v1/authenticate";
        final HttpPost post =   new HttpPost(url);
        List<NameValuePair> form = new ArrayList<NameValuePair>();
        form.add(new BasicNameValuePair("client_id", consumerKey));
        form.add(new BasicNameValuePair("client_secret", consumerSecret));
        form.add(new BasicNameValuePair("grant_type", "client_credentials"));
        post.setEntity(new UrlEncodedFormEntity(form, "UTF-8"));


        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(post);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }

        final BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
        final JSONParser jsonParser = new JSONParser();
        final JSONObject jsonObj = (JSONObject) jsonParser.parse(br);
        return (String)jsonObj.get("token_type") + " " + (String)jsonObj.get("access_token");
    }
    static private class  WorkItemId
    {
        public String id;
        public String userId;
    }
    //submit the workitem described by 'content' parameter. Returns the id of the workitem
    static WorkItemId submitWorkItem(final String token, final String content) throws IOException, ParseException {
        final String url = "https://developer.api.autodesk.com/autocad.io/v1/WorkItems";
        final HttpPost post =   new HttpPost(url);
        post.addHeader("DataServiceVersion","3.0");
        post.addHeader("MaxDataServiceVersion", "3.0");
        post.addHeader("Content-Type", "application/json;odata=minimalmetadata");
        post.addHeader("Accept", "application/json;odata=minimalmetadata");
        post.addHeader("Accept-Charset","UTF-8");
        post.addHeader("Authorization", token);
        post.setEntity(new StringEntity(content));

        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(post);

        if (response.getStatusLine().getStatusCode() != 201) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }
        final BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
        final JSONParser jsonParser = new JSONParser();
        final JSONObject jsonObj = (JSONObject) jsonParser.parse(br);
        final WorkItemId wid = new WorkItemId();
        wid.id = (String)jsonObj.get("Id");
        wid.userId = (String)jsonObj.get("UserId");
        return wid;

    }

    //polls the workitem for its status. Returns the status.
    static String pollWorkItem(final String token, final WorkItemId id) throws IOException, ParseException {
        final String url = String.format("https://developer.api.autodesk.com/autocad.io/v1/WorkItems(Id='%s',UserId='%s')/Status", id.id, id.userId);
        final HttpGet get =   new HttpGet(url);
        get.addHeader("DataServiceVersion","3.0");
        get.addHeader("MaxDataServiceVersion", "3.0");
        get.addHeader("Accept", "application/json;odata=minimalmetadata");
        get.addHeader("Accept-Charset","UTF-8");
        get.addHeader("Authorization", token);

        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(get);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }

        final BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
        final JSONParser jsonParser = new JSONParser();
        final JSONObject jsonObj = (JSONObject) jsonParser.parse(br);
        return (String)jsonObj.get("value");
    }

    //downloads the workitem results and status report.
    static void downloadResults(final String token, final WorkItemId id) throws IOException, ParseException {
        final String url = String.format("https://developer.api.autodesk.com/autocad.io/v1/WorkItems(Id='%s',UserId='%s')", id.id, id.userId);
        final HttpGet get =   new HttpGet(url);
        get.addHeader("DataServiceVersion","3.0");
        get.addHeader("MaxDataServiceVersion", "3.0");
        get.addHeader("Accept", "application/json;odata=minimalmetadata");
        get.addHeader("Accept-Charset","UTF-8");
        get.addHeader("Authorization", token);

        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(get);

        if (response.getStatusLine().getStatusCode() != 200) {
            throw new RuntimeException("Failed : HTTP error code : "
                    + response.getStatusLine().getStatusCode());
        }

        final BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
        final JSONParser jsonParser = new JSONParser();
        final JSONObject jsonObj = (JSONObject) jsonParser.parse(br);
        final String outputURL = (String)((JSONObject)((JSONArray)((JSONObject) jsonObj.get("Arguments")).get("OutputArguments")).get(0)).get("Resource");
        downloadFile(outputURL, "result.pdf");

        final String reportUrl = (String)((JSONObject) jsonObj.get("StatusDetails")).get("Report");
        downloadFile(reportUrl, "report.txt");

    }

    //help function: downloads a file from an url
    static void downloadFile(final String url, final String fname) throws IOException {
        final HttpClient client = HttpClientBuilder.create().build();
        final HttpResponse response = client.execute(new HttpGet(url));
        final File file = new File(fname);
        final FileOutputStream fop = new FileOutputStream(file);
        response.getEntity().writeTo(fop);
        fop.flush();
        fop.close();
        System.out.println(String.format("Downloaded %s", fname));
    }
}
