package edu.buffalo.cse.cse486586.simpledynamo;

/**
 * Created by kamakshishete on 04/04/16.
 */
public class ChordNode {

    public String NodeId=null;
    public int myPort=-3;
    public String successorId=null;
    public int successorPort=-3;
    public String predecesssorId=null;
    public int predecesssorPort=-3;

    //String portStr=null;


    public ChordNode(String nodeid,int myport,String successorid,int successorport,String predecesssorid,int predecessorport)
    {
        NodeId=nodeid;
        myPort=myport;
        successorId=successorid;
        successorPort=successorport;
        predecesssorId=predecesssorid;
        predecesssorPort= predecessorport;

    }

}
