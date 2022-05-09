package indi.endlesswork.web3j.web3jdemo.trade;

import lombok.extern.slf4j.Slf4j;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthTransaction;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;

import java.io.IOException;
import java.math.BigInteger;

/**
 * @author endlesswork
 * @date 2022-04-30 11:54
 * @desc 已知交易hash去校验交易信息
 */
@Slf4j
public class TradeCheckDemo {

    public static BigInteger minBlock = BigInteger.valueOf(20);

    public static final BigInteger CONTRACT_ACCURACY = BigInteger.valueOf(1_000_000_000_000_000_000L);


    public static void main(String args[]){
        //这里我们先采用币安的从rpc节点，这个更容易发生获取不到交易的区块高度的情况
        Web3j web3j = Web3j.build(new HttpService("https://bsc-dataseed1.binance.org/"));
        //String hash = "0x57a9158c8efbd5be251ade5ab27a2010b5b3be44ef4ba8e5d92208704207dbe4";
        String hash = "0x46f88aab3b6c2c0d1d88875eebbaee2e5bda1d2f9b26f46a6a5e4513fc4125b2";
        check(web3j, hash);
    }

    public static void check(Web3j web3j, String hash){
        /**
         * 需要去判断当前交易状态
         * */
        BigInteger status = getTransactionStatus(web3j, hash);
        if (!BigInteger.ONE.equals(status)){
            log.error("error transaction");
            return;
        }

        getTransactionInfo(web3j, hash, true);

    }

    /**
     * 获取交易信息
     *
     * @param web3j
     * @param hash
     * @return void
     */
    public static void getTransactionInfo(Web3j web3j, String hash,  boolean retry){
        Request<?, EthTransaction> request= web3j.ethGetTransactionByHash(hash);
        BigInteger currentBlockNumber = null;
        Transaction transaction = null;
        try {
            currentBlockNumber = web3j.ethBlockNumber().send().getBlockNumber();
            transaction = request.send().getTransaction().get();
        }catch (Exception e){
            log.error("web3j check transaction error");
        }
        if (transaction==null){
            log.error("web3j check transaction info is null");
            return;
        }
        /**
         * getBlockNumber需要try catch住，
         * 直接判断transaction.getBlockNumber()==null也会抛出异常，这个多见于从节点获取不到交易的区块高度
         * （主节点和从节点数据差异有可能在小时级别）
         * */
        try {
            BigInteger bk= transaction.getBlockNumber();
        }catch (Exception e){
            log.error("transaction BlockNumber is null");
            //尝试去其他节点获取一次
            if (retry){
                log.error("start retry get transaction");
                Web3j master = Web3j.build(new HttpService("https://bsc-dataseed.binance.org/"));
                getTransactionInfo(master,hash, false);
            }
            return;
        }
        /**
         * 我们还需要判断当前链上的区块高度大于合约发生的区块高度的是否相差到了一个我们要求的差值
         * 至于为什么这样去做 可以百度下区块高度、交易确认数，这块放上一个地址
         * https://zoyi14.smartapps.cn/pages/note/index?slug=7faa3ed42d28&origin=share&_swebfr=1&_swebFromHost=mibrowser
         * */
        if (currentBlockNumber.subtract(transaction.getBlockNumber()).compareTo(minBlock)!=1){
            return;
        }
        //这里开始我们分解下参数
        String input = transaction.getInput();
        String contractAddress = transaction.getTo();
        String from = transaction.getFrom();
        BigInteger nonce= transaction.getNonce();
        BigInteger gasPrice = transaction.getGasPrice();
        if(input.length()<138){
            log.error("check transaction input error");
            return;
        }
        String function = input.substring(0,10);
        String amount_s = input.substring(74,138);
        BigInteger amount = new BigInteger(amount_s, 16).divide(CONTRACT_ACCURACY);
        String to_s = input.substring(10,74);
        String to = ("0x"+ to_s.substring(24,64)).toLowerCase();
        log.info("transaction info hash:{}, contract: {}, from: {} , to :{}, value:{}, nonce:{}, gasPrice:{}",
                hash, contractAddress, from, to, amount, nonce, gasPrice);


    }

    /**
     * 获取交易状态 1:success
     *             0:fail
     *            -1:pending or not exists
     *
     * @param web3j
     * @param hash
     * @return BigInteger
     */
    public static BigInteger getTransactionStatus(Web3j web3j, String hash) {
        EthGetTransactionReceipt send = null;
        try {
            send = web3j.ethGetTransactionReceipt(hash).send();
        } catch (IOException e) {
            log.error("get transaction status error");
        }
        TransactionReceipt receipt = send.getTransactionReceipt().orElse(null);
        if (receipt == null) {
            log.error("get transaction status is null");
            return BigInteger.valueOf(-1);
        }
        String value = receipt.getStatus();
        if (value.length() <= 2) {
            return BigInteger.ZERO;
        }
        return new BigInteger(value.substring(2), 16);
    }

}
