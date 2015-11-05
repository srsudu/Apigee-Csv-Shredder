// CsvShredder.java
//
// This is the source code for a Java callout for Apigee Edge.  This
// callout is very simple - it shreds a CSV, then sets a Java Map into a
// context variable, and then returns SUCCESS.
//
// ------------------------------------------------------------------

package com.dinochiesa.edgecallouts;

import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.io.InputStreamReader;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.execution.spi.Execution;
import com.apigee.flow.message.MessageContext;
import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.message.Message;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CsvShredder implements Execution {

    private final ObjectMapper om = new ObjectMapper();

    private Map properties; // read-only

    public CsvShredder(Map properties) {
        this.properties = properties;
    }

    private List<String> getFieldList(MessageContext msgCtxt) throws IllegalStateException {
        String fieldlist = (String) this.properties.get("fieldlist");
        if (fieldlist == null || fieldlist.equals("")) {
            return null; // assume the first row gives the field list
            //throw new IllegalStateException("fieldlist is null or empty.");
        }
        fieldlist = resolvePropertyValue(fieldlist, msgCtxt);
        if (fieldlist == null || fieldlist.equals("")) {
            return null; // assume the first row gives the field list
            //throw new IllegalStateException("fieldlist resolves to null or empty.");
        }

        // now split by commas
        String[] parts = StringUtils.split(fieldlist,",");
        List<String> list = new ArrayList<String>();
        for(int i=0; i< parts.length; i++) {
            list.add(parts[i].trim());
        }
        return list;
    }


    // If the value of a property value begins and ends with curlies,
    // and has no intervening spaces, eg, {apiproxy.name}, then
    // "resolve" the value by de-referencing the context variable whose
    // name appears between the curlies.
    private String resolvePropertyValue(String spec, MessageContext msgCtxt) {
        if (spec.startsWith("{") && spec.endsWith("}") && (spec.indexOf(" ") == -1)) {
            String varname = spec.substring(1,spec.length() - 1);
            String value = msgCtxt.getVariable(varname);
            return value;
        }
        return spec;
    }


    public ExecutionResult execute (final MessageContext msgCtxt,
                                    final ExecutionContext execContext) {
        Message msg = msgCtxt.getMessage();
        String varprefix= "csv_";
        String varName = null;
        try {
            List<String> list = getFieldList(msgCtxt);
            InputStreamReader in = new InputStreamReader(msg.getContentAsStream());

            // see info for handling header records here:
            // https://commons.apache.org/proper/commons-csv/apidocs/org/apache/commons/csv/CSVFormat.html

            Map<String, Object> map = new HashMap<String, Object>();
            Iterable<CSVRecord> records = null;
            if (list == null) {
                records = CSVFormat.DEFAULT.withHeader().parse(in);
            }
            else {
                records = CSVFormat.DEFAULT.withHeader(list.toArray(new String[1])).parse(in);
            }

            for (CSVRecord record : records) {
                // assume first field is the primary key
                String firstField = record.get(0);
                map.put(firstField, record.toMap());
            }

            // set a variable to hold the Map<String, Map>
            msgCtxt.setVariable(varprefix + "result_java", map);

            String jsonResult = om.writer()
                .withDefaultPrettyPrinter()
                .writeValueAsString(map);

            // set another variable to hold the json representation
            msgCtxt.setVariable(varprefix + "result_json", jsonResult);
        }
        catch (java.lang.Exception exc1) {
            exc1.printStackTrace();
            varName = varprefix + "error";
            msgCtxt.setVariable(varName, exc1.getMessage());
            varName = varprefix + "stacktrace";
            msgCtxt.setVariable(varName, ExceptionUtils.getStackTrace(exc1));
            return ExecutionResult.ABORT;
        }

        return ExecutionResult.SUCCESS;
    }
}
