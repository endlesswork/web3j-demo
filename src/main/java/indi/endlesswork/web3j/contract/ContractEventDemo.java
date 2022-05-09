package indi.endlesswork.web3j.contract;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * @author endlesswork
 * @date 2022-05-09 21:54
 * @desc 合约事件监听
 */
@Slf4j
public class ContractEventDemo {

    private static final String CONTRACT_ADDRESS = "0xbb4cdb9cbd36b01bd1cbaebf2de08d9173bc095c";

    public static void main(String args[]){
        Web3j web3j = Web3j.build(new HttpService("https://bsc-dataseed.binance.org/"));
        contractEvent(web3j);
    }


    /**
     * 监听转账事件（这个可能会发生断开）
     *
     * @param web3j
     * @return void
     */
    public static void contractEvent(Web3j web3j){
        log.info("start monitor contract");
        /**
         * 监听ERC20 最新的转账交易
         *
         */
        EthFilter filter = new EthFilter(
                DefaultBlockParameterName.LATEST,
                DefaultBlockParameterName.LATEST,
                CONTRACT_ADDRESS);
        Event event = new Event("Transfer",
                Arrays.<TypeReference<?>>asList(
                        new TypeReference<Address>(true) {
                        },
                        new TypeReference<Address>(true) {
                        }, new TypeReference<Uint256>(false) {
                        }
                )
        );
        String topicData = EventEncoder.encode(event);
        filter.addSingleTopic(topicData);

        web3j.ethLogFlowable(filter).doOnError(e->{
            log.error(e.toString());
        }).subscribe(tradeLog -> {
            log.info("transfer info:{}", JSON.toJSONString(tradeLog));
            EventValues eventValues = Contract.staticExtractEventParameters(event, tradeLog);
            String hash = tradeLog.getTransactionHash();
            log.info("transfer eventValues:{}", JSON.toJSONString(eventValues));
            if (eventValues!=null&& eventValues.getIndexedValues()!=null&&
                    eventValues.getNonIndexedValues()!=null ){
                String from = (String) eventValues.getIndexedValues().get(0).getValue();
                String to = (String) eventValues.getIndexedValues().get(1).getValue();
                BigInteger amount = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                log.info("transfer from:{}，to：{}, amount: {}", from, to, amount);

            }
        });

    }

}
