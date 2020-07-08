package com.broadcom.apmsql;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.broadcom.apm.WilyMetricReporter;

public class EPAgentStateless {
	
	private static String address = "";
	private static String token = "";
	private static Properties props;
	private static ArrayList<String> queries;
	private static ArrayList<String> metrics;
	private static long length;
	private static boolean debug;
	private static boolean stateful;
	private static long delay;
	
	public static void main(String[] args) throws Exception {
		main(args, System.out);
		//getProps();
		//length = 150000;
		//address = "http://spectrum.forwardinc.biz:8080/spectrum/restful/model/{MODEL}?{ATTRIBUTES}";
		//token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJCV0FUU09OIiwiZHluZXhwIjp0cnVlLCJ0aWQiOjg4OCwianRpIjoiYmZlNjg3ZTMtNzYyNy00MmU0LWFmZjUtNDNiZTViYjRjYzQxIn0.i5JyoS6iAqbEOG8A3cCZP4qZ1tqwpxjbsGbP0XBgHF51idWkBJQEW3bQXuuwK_H_I0ferHEIp0OYTgJbIP0SQA";
	}
	
	public static void main(String[] args, PrintStream psEpa) throws Exception {
		debug = false;
		getProps(args[0]);
		loopQueries(psEpa);
		//getProps();
		//length = 150000;
		//address = "http://spectrum.forwardinc.biz:8080/spectrum/restful/model/{MODEL}?{ATTRIBUTES}";
		//token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJCV0FUU09OIiwiZHluZXhwIjp0cnVlLCJ0aWQiOjg4OCwianRpIjoiYmZlNjg3ZTMtNzYyNy00MmU0LWFmZjUtNDNiZTViYjRjYzQxIn0.i5JyoS6iAqbEOG8A3cCZP4qZ1tqwpxjbsGbP0XBgHF51idWkBJQEW3bQXuuwK_H_I0ferHEIp0OYTgJbIP0SQA";
	}
	
	private static void loopQueries(PrintStream psEpa) throws UnsupportedEncodingException, IOException {
		metrics = new ArrayList<>();
		for(String query : queries) {
			String end = System.currentTimeMillis() + "";
			String start = (System.currentTimeMillis() - length) + "";
			String body = "{ \"query\" : \"" + query + "\"}";
			String jsonResult = getJSONResult(body.replace("{START}", start).replace("{END}", end));
			addMetricsFromJSON(jsonResult);
		}
		for(String metric : metrics) {
			printMetric(metric, psEpa);
		}
	}
	
	private static void printMetric(String row, PrintStream psEpa) throws UnsupportedEncodingException, IOException {
		row = row.replace("[", "").replace("]", "");
		//System.out.println(row);
		String[] split = row.split(",");
		String name = split[1].replace("\"", "") + "|" + split[2].replace("\"", "") + "|" + split[3].replace("\"", "") + "|" + split[5].replace("\"", "");
		String metric = split[split.length - 1].replace("\"", "");
		long min = Long.parseLong(split[split.length - 4].replace("\"", ""));
		long value = Long.parseLong(split[split.length - 1].replace("\"", ""));
		String type = "";
		if(name.toLowerCase().contains("average")) {
			type = "LongAverage";
		} else if(name.toLowerCase().contains("per interval")) {
			type = "PerIntervalCounter";
		} else {
			type = "LongCounter";
		}
		WilyMetricReporter.printMetric(type, name, metric, psEpa);
	}
    
    public static void addMetricsFromJSON(String json) {
    	if(debug) {
    		System.out.println(json);
    	}
        JSONObject jsonObject = new JSONObject(json);
        JSONArray ja = (JSONArray) jsonObject.get("rows");
        try {
            for (int x = 0; x < ja.length(); x++) {
            	//System.out.println(ja.get(x).toString());
            	metrics.add(ja.get(x).toString());
            }
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    private static void getProps(String propertiesFile) {
    	queries = new ArrayList<>();
        props = new Properties();
        //String propertiesFile = System.getProperty("props");
        try {
            props.load(new FileInputStream(propertiesFile));
        } catch (FileNotFoundException ex) {
        	ex.printStackTrace();
        } catch (IOException ex) {
        	ex.printStackTrace();
        }
        for(int x = 0; x < 100; x++) {
            if(props.containsKey("apm.query." + x)) {
            	queries.add(props.getProperty("apm.query." + x));
            }
        }
        length = Long.parseLong(props.getProperty("apm.query.time.length"));
        address = props.getProperty("apm.api.url");
        token = props.getProperty("apm.api.token");
        if(props.containsKey("apm.api.debug")) {
        	debug = Boolean.parseBoolean(props.getProperty("apm.api.debug"));
        } else {
        	debug = false;
        }
        if(props.containsKey("apm.agent.stateful")) {
        	stateful = Boolean.parseBoolean(props.getProperty("apm.agent.stateful"));
        	delay = Long.parseLong(props.getProperty("apm.agent.delay"));
        } else {
        	stateful = false;
        }
    }

	public static String getJSONResult(String body) {
    	long start = System.currentTimeMillis();
        String result = "";
        try {
            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes());
            os.flush();
            
            /*if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP Error code : "
                        + conn.getResponseCode());
            }*/
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String output;
            while ((output = br.readLine()) != null) {
                result += output + "\n";
            }
            if(debug) {
            	System.out.println(result);
            }
            conn.disconnect();

        } catch (IOException e) {
            System.out.println("Exception in NetClientGet:- " + e);
        } catch (RuntimeException e) {
            System.out.println("Exception in NetClientGet:- " + e);
        }
        long end = System.currentTimeMillis();
        if(debug) {
        	System.out.println("Query " + body + " took " + ((end - start) / 1000.0) + " seconds.");
        }
        return result;
    }

}
