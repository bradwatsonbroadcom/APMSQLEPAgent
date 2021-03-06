package com.broadcom.apmsql;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.broadcom.apm.WilyMetric;
import com.broadcom.apm.WilyMetricReporter;

public class EPAgentStateless {
	
	private static String address = "";
	private static String token = "";
	private static Properties props;
	private static ArrayList<String> queries;
	private static ArrayList<String> metricNames;
	private static ArrayList<WilyMetric> metrics;
	private static long length;
	private static int timeout;
	private static boolean debug;
	private static boolean stateful;
	private static long delay;
	private static long retryDelay = 0;
	private static int retries;
	
	public static void main(String[] args) throws Exception {
		main(args, System.out);
	}
	
	public static void main(String[] args, PrintStream psEpa) throws Exception {
		debug = false;
		//getProps(args[0]);
		getProps("C:/CAWILY/apmsqlpersonal.properties");
		loopQueries(psEpa);
		printMetrics(psEpa);
	}
	
	private static void printMetrics(PrintStream psEpa) {
		for(WilyMetric metric : metrics) {
			WilyMetricReporter.printMetric(metric, psEpa);
		}
	}
	private static void loopQueries(PrintStream psEpa) throws UnsupportedEncodingException, IOException {
		metrics = new ArrayList<>();
		metricNames = new ArrayList<>();
		for(String query : queries) {
			String end = System.currentTimeMillis() + "";
			String start = (System.currentTimeMillis() - length) + "";
			String body = "{ \"query\" : \"" + query + "\"}";
			String jsonResult = getJSONResult(body.replace("{START}", start).replace("{END}", end));
			int x = 1;
			while(x < retries) {
				try {
					if(retryDelay > 0) {
						Thread.sleep(retryDelay);
					}
					jsonResult = getJSONResult(body.replace("{START}", start).replace("{END}", end));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				x++;
			}
			if(null != jsonResult && !isEmpty(jsonResult)) {
				addMetricsFromJSON(jsonResult);
			}
		}
	}
	
	private static boolean isEmpty(String json) {
		try {
			JSONObject jsonObject = new JSONObject(json);
	        JSONArray ja = (JSONArray) jsonObject.get("rows");
	        if(ja.length() == 0) {
	        	return true;
	        }
		} catch (Exception e) {
			System.out.println("JSON: " + json);
			e.printStackTrace();
		}
		return false;
	}

	private static void parseMetric(String row) throws UnsupportedEncodingException, IOException {
		row = row.replace("[", "").replace("]", "");
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
		if(!metricNames.contains(name)) {
			metricNames.add(name);
			metrics.add(new WilyMetric(name, metric, type));
		} else {
			for(WilyMetric m : metrics) {
				if(m.getName().trim().equalsIgnoreCase(name.trim())) {
					m.setValue(metric);
				}
			}
		}
	}
    
    public static void addMetricsFromJSON(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            JSONArray ja = (JSONArray) jsonObject.get("rows");
            for (int x = 0; x < ja.length(); x++) {
            	parseMetric(ja.get(x).toString());
            }
        }catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private static void getProps(String propertiesFile) {
    	queries = new ArrayList<>();
        props = new Properties();
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
        if(props.containsKey("apm.api.retry.count")) {
        	retries = Integer.parseInt(props.getProperty("apm.api.retry.count"));
        	retryDelay = Long.parseLong(props.getProperty("apm.api.retry.delay"));
        } else {
        	retries = 0;
        }
        if(props.containsKey("apm.api.timeout")) {
        	timeout = Integer.parseInt(props.getProperty("apm.api.timeout"));
        } else {
        	timeout = 0;
        }
    }

	public static String getJSONResult(String body) {
		if(debug) {
			System.out.println("URL: " + address);
			System.out.println("API Key: " + token);
		}
    	long start = System.currentTimeMillis();
        String result = "";
        try {
            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Cache-Control", "no-cache");
            if(timeout > 0) {
            	conn.setConnectTimeout(timeout);
            }
            conn.setDoOutput(true);
            
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes());
            os.flush();
            
            if(debug) {
            	System.out.println("Response code: " + conn.getResponseCode());
            }
            InputStreamReader in = new InputStreamReader(conn.getInputStream());
            BufferedReader br = new BufferedReader(in);
            String output;
            while ((output = br.readLine()) != null) {
            	if(debug) {
            		System.out.println(output);
            	}
                result += output + "\r\n";
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
