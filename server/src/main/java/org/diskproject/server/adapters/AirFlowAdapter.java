package org.diskproject.server.adapters;

import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.diskproject.shared.classes.loi.LineOfInquiry;

public class AirFlowAdapter extends MethodAdapter {
    private PoolingHttpClientConnectionManager connectionManager;
    private CloseableHttpClient httpClient;

    public AirFlowAdapter (String adapterName, String url, String username, String password) {
        super(adapterName, url, username, password);
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, "X};83^adj4cC`Z&>"));
        
        connectionManager = new PoolingHttpClientConnectionManager();
		httpClient = HttpClients.custom()
	              .setConnectionManager(connectionManager)
	              .setDefaultCredentialsProvider(credentialsProvider)
	              .build();
    }

    @Override
    public boolean validateLOI (LineOfInquiry loi, Map<String, String> values) {
        return false;
    }
    
    public void getMethodList () {
		HttpGet userInfo = new HttpGet(this.getEndpointUrl() + "/dags?limit=100&only_active=true");
		userInfo.addHeader(HttpHeaders.ACCEPT, "application/json");
		
		try (CloseableHttpResponse httpResponse = httpClient.execute(userInfo)) {
		    HttpEntity responseEntity = httpResponse.getEntity();
		    String strResponse = EntityUtils.toString(responseEntity);
		    System.out.println(strResponse);
		} catch (Exception e) {
            System.err.println("Could not list methods");
        }
    }
}
