// CsvShredderTest.java
// ------------------------------------------------------------------
//
// Last saved: <2021-July-07 16:36:19>
// ------------------------------------------------------------------
//

package com.google.apigee.callouts;

import com.apigee.flow.execution.ExecutionContext;
import com.apigee.flow.execution.ExecutionResult;
import com.apigee.flow.message.Message;
import com.apigee.flow.message.MessageContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import mockit.Mock;
import mockit.MockUp;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class CsvShredderTest {
  private static final String testDataDir = "src/test/resources/test-data";

  MessageContext msgCtxt;
  String messageContent;
  Message message;
  ExecutionContext exeCtxt;

  @BeforeMethod()
  public void testSetup1() {

    msgCtxt =
        new MockUp<MessageContext>() {
          private Map variables;

          public void $init() {
            variables = new HashMap();
          }

          @Mock()
          public <T> T getVariable(final String name) {
            if (variables == null) {
              variables = new HashMap();
            }
            return (T) variables.get(name);
          }

          @Mock()
          public boolean setVariable(final String name, final Object value) {
            if (variables == null) {
              variables = new HashMap();
            }
            variables.put(name, value);
            return true;
          }

          @Mock()
          public boolean removeVariable(final String name) {
            if (variables == null) {
              variables = new HashMap();
            }
            if (variables.containsKey(name)) {
              variables.remove(name);
            }
            return true;
          }

          @Mock()
          public Message getMessage() {
            return message;
          }
        }.getMockInstance();

    exeCtxt = new MockUp<ExecutionContext>() {}.getMockInstance();

    message =
        new MockUp<Message>() {
          @Mock()
          public InputStream getContentAsStream() {
            return new ByteArrayInputStream(messageContent.getBytes(StandardCharsets.UTF_8));
          }
        }.getMockInstance();
  }

  private String readAllText(String filename) throws IOException {
    return new String(Files.readAllBytes(Paths.get(testDataDir, filename)), StandardCharsets.UTF_8);
  }

  @Test
  public void basicRead() throws Exception {
    messageContent = readAllText("sample5.csv");
    Properties properties = new Properties();
    CsvShredder callout = new CsvShredder(properties);
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    Assert.assertEquals(actualResult, ExecutionResult.SUCCESS);
    Map<String, Map<String, String>> result =
        (Map<String, Map<String, String>>) msgCtxt.getVariable("csv_result_java");
    Assert.assertEquals(result.size(), 5);
    Assert.assertNotNull(result.get("6001 MCMAHON DR"));
    Assert.assertEquals(result.get("6001 MCMAHON DR").get("price"), "81900");
  }

  @Test
  public void largeContentRead() throws Exception {
    messageContent = readAllText("sample1000.csv");
    Properties properties = new Properties();
    properties.put("trim-spaces", "true");
    CsvShredder callout = new CsvShredder(properties);
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    Assert.assertEquals(actualResult, ExecutionResult.SUCCESS);

    Assert.assertEquals(msgCtxt.getVariable("csv_rows_read"), "1000");

    Map<String, Map<String, String>> result =
        (Map<String, Map<String, String>>) msgCtxt.getVariable("csv_result_java");

    Assert.assertEquals(result.size(), 1000);
    Assert.assertNotNull(result.get("000165"));
    Assert.assertEquals(result.get("000165").get("RAND"), "24974");
    Assert.assertNotNull(result.get("000672"));
    Assert.assertEquals(result.get("000672").get("RAND"), "42656");
    Assert.assertNotNull(result.get("000988"));
    Assert.assertEquals(result.get("000988").get("RAND"), "25784");
  }

  @Test
  public void rulesReadList() throws Exception {
    messageContent = readAllText("sample37.csv");
    Properties properties = new Properties();
    properties.put("output-format", "list");
    CsvShredder callout = new CsvShredder(properties);
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    Assert.assertEquals(actualResult, ExecutionResult.SUCCESS);
    Assert.assertEquals(msgCtxt.getVariable("csv_rows_read"), "37");
    List<Map<String, String>> result =
        (List<Map<String, String>>) msgCtxt.getVariable("csv_result_java");
    Assert.assertEquals(result.size(), 37);
    Map<String, String> item = result.get(13);
    Assert.assertNotNull(item);
    Assert.assertEquals(item.get("#Method"), "POST");
    Assert.assertEquals(
        item.get("URI Resource Path"), "/DBTLN/v2/accounts/{AcctNbr}/notes/{NoteNbr}/collateral");
  }

  @Test
  public void rulesReadMap() throws Exception {
    messageContent = readAllText("sample37.csv");
    Properties properties = new Properties();
    properties.put("output-format", "map");
    CsvShredder callout = new CsvShredder(properties);
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    Assert.assertEquals(actualResult, ExecutionResult.SUCCESS);
    Assert.assertEquals(msgCtxt.getVariable("csv_rows_read"), "4");
    Map<String, Map<String, String>> result =
        (Map<String, Map<String, String>>) msgCtxt.getVariable("csv_result_java");
    Assert.assertEquals(result.size(), 4);
    Map<String, String> item = result.get("PUT");
    Assert.assertNotNull(item);
    Assert.assertEquals(item.get("#Method"), "PUT");
    // last entry "wins" when the output is a map
    Assert.assertEquals(
        item.get("URI Resource Path"), "/DBTCI/v2/customers/{CustNbr}/phone-numbers/{ResnCde}");
  }

  @Test
  public void rulesReadMapWithContrivedPk() throws Exception {
    messageContent = readAllText("sample37.csv");
    Properties properties = new Properties();
    properties.put("output-format", "map");
    properties.put("contrive-primary-key", "true");
    CsvShredder callout = new CsvShredder(properties);
    ExecutionResult actualResult = callout.execute(msgCtxt, exeCtxt);
    Assert.assertEquals(actualResult, ExecutionResult.SUCCESS);
    Assert.assertEquals(msgCtxt.getVariable("csv_rows_read"), "37");
    Map<String, Map<String, String>> result =
        (Map<String, Map<String, String>>) msgCtxt.getVariable("csv_result_java");
    Assert.assertEquals(result.size(), 37);
    Map<String, String> item = result.get("0000000025");
    Assert.assertNotNull(item);
    Assert.assertEquals(item.get("#Method"), "DELETE");
    Assert.assertEquals(
        item.get("URI Resource Path"),
        "/DBTFM/v2/scheduled-transfers/{SysCde}/{SndAcctNbr}/{SeqNbr}");
  }
}
