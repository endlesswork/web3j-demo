package indi.endlesswork.web3j.trade;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGasPrice;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

/**
 * @author endlesswork
 * @date 2022-04-30 14:11
 * @desc 通过私钥进行转账
 */
@Slf4j
public class TransferDemo {

    /**
     * 钱包账户私钥
     ***/
    private static final String PRIVATE_KEY = "";

    private static final long CHAIN_ID = 56;

    private static final String CONTRACT_ADDRESS = "";

    public static void main(String args[]){
        Web3j web3j = Web3j.build(new HttpService("https://bsc-dataseed1.binance.org/"));
        String to = "";
        transfer(web3j, CONTRACT_ADDRESS,
                PRIVATE_KEY,
                to, BigInteger.valueOf(100)
                        .multiply(BigInteger.valueOf(1_000_000_000_000_000_000L)),
                getGasPrice(web3j), BigInteger.valueOf(600000L));
    }


    /**
     * 获取链上gas
     *
     * @param web3j
     * @return
     */
    public static BigInteger getGasPrice(Web3j web3j) {
        EthGasPrice ethGasPrice = null;
        try {
            ethGasPrice = web3j.ethGasPrice().sendAsync().get();
        } catch (InterruptedException e) {
            log.error("get gas price is error");
        } catch (ExecutionException e) {
            log.error("get gas price is error");
        }
        BigInteger gasPrice = ethGasPrice.getGasPrice();
        //默认给个值
        BigInteger price = new BigInteger("5000000000");
        if (gasPrice!=null){
            if (gasPrice.compareTo(price)==-1){
                gasPrice=price;
            }
        }else {
            gasPrice=price;
        }
        return gasPrice;
    }

    /**
     * 根据私钥获取地址
     *
     * @param privateKey
     * @return
     */
    public static String getAddressByPrivateKey(String privateKey) {
        ECKeyPair ecKeyPair = ECKeyPair.create(new BigInteger(privateKey, 16));
        return "0x" + Keys.getAddress(ecKeyPair).toLowerCase();
    }

    /**
     * 转账
     *
     * @param web3j
     * @param contractAddress 合约地址
     * @param privateKey      账号私钥
     * @param to              收款地址
     * @param amount          虚拟币额度
     * @param gasPrice             虚拟币额度
     * @param gasLimit        虚拟币额度
     * @return
     */
    public static String transfer(Web3j web3j, String contractAddress, String privateKey,
                                   String to, BigInteger amount, BigInteger gasPrice, BigInteger gasLimit)  {

        String from = getAddressByPrivateKey(privateKey);
        try {
            //用私钥加载转账凭证
            Credentials credentials = Credentials.create(privateKey);
            //获取交易笔数nonce
            BigInteger nonce = getNonce(web3j, from);
            //合约转账方法
            Function function = new Function(
                    "transfer",
                    Arrays.asList(new Address(to), new Uint256(amount)),
                    Collections.singletonList(new TypeReference<Type>() {
                    }));
            //创建RawTransaction交易对象
            String encodedFunction = FunctionEncoder.encode(function);
            RawTransaction rawTransaction = RawTransaction.createTransaction(nonce, gasPrice, gasLimit,
                    contractAddress, encodedFunction);
            //签名Transaction
            byte[] signMessage = TransactionEncoder.signMessage(rawTransaction,CHAIN_ID, credentials);
            String hexValue = Numeric.toHexString(signMessage);
            //发送交易
            EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).sendAsync().get();
            if (ethSendTransaction.getError() != null) {
                log.error("transfer error: {}", JSONObject.toJSONString(ethSendTransaction.getError()));
                //实际开发环境我们也需要去重新获取地址上的nonce，避免nonce+1了，但是影响后面的订单
                return null;
            }
            String hash = ethSendTransaction.getTransactionHash();
            if (hash != null) {
                /**注意这里返回hash并不一定代表交易成功了,
                 * 有可能因为手续费或者余额等其他因素并不会成功，需要再通过
                 * TradeCheckDemo中check方法校验一遍
                 */
                log.info("transfer from: {}, to: {}, hash: {}, nonce:{}, value:{}, gas:{}",
                        from , to , hash, nonce, amount.toString(), gasPrice);
                return hash;
            } else {
                //实际开发环境我们也需要去重新获取地址上的nonce
                log.error("transfer error, hash is null");

            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            //实际开发环境我们也需要去重新获取地址上的nonce
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * 获取nonce
     *
     * @param web3j
     * @param address 钱包地址
     * @return
     */
    public  static BigInteger getNonce(Web3j web3j, String address) throws IOException {
        EthGetTransactionCount value = web3j.ethGetTransactionCount(address, DefaultBlockParameterName.LATEST).send();
        long l = value.getTransactionCount().longValue();
        return BigInteger.valueOf(l);
    }
}
