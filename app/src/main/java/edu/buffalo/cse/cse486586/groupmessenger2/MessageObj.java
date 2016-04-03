package edu.buffalo.cse.cse486586.groupmessenger2;

/**
 * Created by civa on 3/4/16.
 */
public class MessageObj {

    public boolean isFailed;
    public float SeqNo;
    public String msg;
    public String msgNo;
    public String msgFrom;
    public boolean status;
    public int proposalCnt;

    public MessageObj(float SeqNo, String msgFrom, String msgNo, String msg,boolean status, boolean isFailed, int proposalCnt){
//    public MessageObj(String msg,boolean status, boolean delivered){
        this.SeqNo = SeqNo;
        this.msgFrom = msgFrom;
        this.msgNo = msgNo;
        this.msg = msg;
        this.status = status;
        this.isFailed = isFailed;
        this.proposalCnt = proposalCnt;
    }

}
