package br.com.vinirib.pact.consumer.client.pact;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.DslPart;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import br.com.vinirib.pact.consumer.client.dto.BalanceDTO;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.javamoney.moneta.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.zalando.gson.money.MoneyTypeAdapterFactory;

import javax.money.Monetary;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "AccountBalanceProvider", port = "1234")
public class AccountPactTest {

    private static final String BALANCE_URL_WORKING = "/v1/accounts/balance/1";
    private static final String BALANCE_URL_NOT_WORKING = "/v1/accounts/balance/1000";
    private final Map<String, String> headers = MapUtils.putAll(new HashMap<>(), new String[]{
            "Content-Type", "application/json"
    });

    private final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.FINAL, Modifier.TRANSIENT, Modifier.STATIC)
            .registerTypeAdapterFactory(new MoneyTypeAdapterFactory())
            .serializeNulls()
            .create();

    @Pact(provider = "AccountBalanceProvider", consumer = "AccountBalanceConsumer")
    public RequestResponsePact balanceEndpointTest(PactDslWithProvider builder) {

        DslPart bodyResponse = new PactDslJsonBody()
                .integerType("accountId", 1)
                .integerType("clientId", 1)
                .object("balance")
                .decimalType("amount", 100.00)
                .stringType("currency", "BRL")
                .closeObject();

        return builder
                .given("get balance of accountId 1")
                .uponReceiving("A request to " + BALANCE_URL_WORKING)
                .path(BALANCE_URL_WORKING)
                .method("GET")
                .willRespondWith()
                .headers(headers)
                .status(200)
                .body(bodyResponse)
                .toPact();
    }

    @Pact(provider = "AccountBalanceProvider", consumer = "AccountBalanceConsumer")
    public RequestResponsePact balanceEndpointNotWorkingTest(PactDslWithProvider builder) {
        return builder
                .given("No accounts exist from accountId 1000")
                .uponReceiving("A request to " + BALANCE_URL_NOT_WORKING)
                .path(BALANCE_URL_NOT_WORKING)
                .method("GET")
                .willRespondWith()
                .status(404)
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "balanceEndpointTest", providerName = "AccountBalanceProvider")
    void testBalanceWorking(MockServer mockServer) throws IOException {
        HttpResponse httpResponse = Request.Get(mockServer.getUrl() + BALANCE_URL_WORKING).execute().returnResponse();
        assertThat(httpResponse.getStatusLine().getStatusCode(), is(equalTo(200)));
        final BalanceDTO balanceDTO = gson
                .fromJson(IOUtils.toString(httpResponse.getEntity().getContent()), BalanceDTO.class);
        assertThat(balanceDTO.getAccountId(), is(1));
        assertThat(balanceDTO.getClientId(), is(1));
        assertThat(balanceDTO.getBalance(), is(Money.of(100.00,
                Monetary.getCurrency("BRL"))));
    }

    @Test
    @PactTestFor(pactMethod = "balanceEndpointNotWorkingTest", providerName = "AccountBalanceProvider")
    void testBalanceNotWorking(MockServer mockServer) throws IOException {
        HttpResponse httpResponse = Request.Get(mockServer.getUrl() + BALANCE_URL_NOT_WORKING).execute().returnResponse();
        assertThat(httpResponse.getStatusLine().getStatusCode(), is(equalTo(404)));
        assertThat(IOUtils.toString(httpResponse.getEntity().getContent()), is(equalTo("")));
    }

}