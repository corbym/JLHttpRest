package uk.co.binarysoup.resthandle;

import net.freeutils.httpserver.HTTPServer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import java.io.IOException;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

public class RestContextGETHandlerIntegrationTest {
    private static final int PORT = 9090;

    private static final HTTPServer server = new HTTPServer(PORT);
    private static final String BASE_URL = "http://localhost:" + PORT;
    private static final GetResource getResource = new GetResource();

    private final HttpClient client = HttpClientBuilder.create().build();

    @BeforeClass
    public static void setUpTestServer() throws IOException {
        final HTTPServer.VirtualHost host = new HTTPServer.VirtualHost(null);
        server.addVirtualHost(host);
        host.addContexts(new RestContextHandler(getResource));
        server.start();
    }

    @Before
    public void resetResource() {
        getResource.reset();
    }


    @Test
    public void GET_urlWithoutPathParam_resourceIsCalled() throws Exception {
        final HttpResponse response = client.execute(new HttpGet(BASE_URL + "/foo"));

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        assertThat(getResource.getCalled, equalTo(true));
    }

    @Test
    public void GET_urlWithQueryParam_resourceIsCalled() throws Exception {
        final HttpResponse response = client.execute(new HttpGet(BASE_URL + "/foo?hey=true"));

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        assertThat(getResource.getWithQueryParamCalled, equalTo(true));
        assertThat(getResource.getCalled, equalTo(false));
    }

    @Test
    public void GET_urlWithPathParam_resourceIsCalled() throws Exception {
        final HttpResponse response = client.execute(new HttpGet(BASE_URL + "/abcdef/foo"));

        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        assertThat(getResource.getWithPathParamCalled, equalTo(true));
        assertThat(getResource.getCalled, equalTo(false));
    }

    @Test
    public void GET_urlWithOneMatchingQueryParam_defaultEndpointIsCalled() throws Exception {
        final HttpResponse response = client.execute(new HttpGet(BASE_URL + "/foo?hey=true&what=false"));


        assertThat(response.getStatusLine().getStatusCode(), equalTo(200));
        assertThat(getResource.getWithQueryParamCalled, equalTo(true));
        assertThat(getResource.getCalled, equalTo(false));
    }

    @Test
    public void GET_urlWithIncorrectPath_resourceIsNotCalled() throws Exception {
        final HttpResponse response = client.execute(new HttpGet(BASE_URL + "/bah"));

        assertThat(response.getStatusLine().getStatusCode(), equalTo(404));
    }

    @AfterClass
    public static void after() {
        server.stop();
    }

    private static class GetResource {

        private boolean getCalled;
        private boolean getWithQueryParamCalled;
        private boolean getWithPathParamCalled;

        @Path("/foo")
        @GET
        public String getStuff() {
            getCalled = true;
            return "ok";
        }

        @Path("/foo")
        @GET
        public String getStuff(@QueryParam("hey") boolean fooing) {
            getWithQueryParamCalled = fooing;
            return "ok";
        }

        @Path("{hey}/foo")
        @GET
        public String getStuffs(@PathParam("hey") boolean fooing) {
            getWithPathParamCalled = fooing;
            return "ok";
        }

        private void reset() {
            getCalled = false;
            getWithQueryParamCalled = false;
        }
    }

}