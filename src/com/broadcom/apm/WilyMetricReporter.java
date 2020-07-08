/**
 *
 * @author Brad Watson
 */
package com.broadcom.apm;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class WilyMetricReporter {
    
    public static void printMetric(String type, String name, String value, PrintStream psEPA) throws UnsupportedEncodingException, IOException {
        String metric = "<metric type=\"" + type + "\" name=\""+ name + "\" value=\"" + value + "\"/>";
		psEPA.println(metric);
    }
    
    public static void printMetric(WilyMetric metric, PrintStream psEPA) {
    	psEPA.println("<metric type=\"" + metric.getType() + "\" name=\""+ metric.getName() + "\" value=\"" + metric.getValue() + "\"/>");
    }
    
}
