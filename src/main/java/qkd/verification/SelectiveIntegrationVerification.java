package qkd.verification;

import network.Circuit;
import request.RequestForConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Regression for direct request mutations, reference path and serialized save states. */
public final class SelectiveIntegrationVerification {
    private static RequestForConnection req(double rate) {
        RequestForConnection r = new RequestForConnection();
        r.setRequiredBitRate(rate);
        return r;
    }
    private static void equal(double actual, double expected, String label) {
        if (Double.doubleToRawLongBits(actual) != Double.doubleToRawLongBits(expected))
            throw new AssertionError(label+": "+actual+" != "+expected);
    }
    public static void main(String[] args) throws Exception {
        Circuit c = new Circuit();
        RequestForConnection a=req(12.5), b=req(7.25), d=req(3.0);
        c.addRequest(a); c.addRequest(b);
        equal(c.getRequiredBitRate(),19.75,"initial");
        equal(c.getRequiredBitRate(),19.75,"cached");
        a.setRequiredBitRate(13.5);
        equal(c.getRequiredBitRate(),20.75,"request.setRequiredBitRate");
        c.getRequests().set(1,d);
        equal(c.getRequiredBitRate(),16.5,"getRequests.set");
        c.getRequests().subList(0,1).set(0,b);
        equal(c.getRequiredBitRate(),10.25,"subList.set");
        java.util.ListIterator<RequestForConnection> iterator=c.getRequests().listIterator();
        iterator.next(); iterator.set(a);
        equal(c.getRequiredBitRate(),16.5,"listIterator.set");
        c.getRequests().remove(1);
        equal(c.getRequiredBitRate(),13.5,"remove");
        c.setRequests(new ArrayList<RequestForConnection>(Arrays.asList(b)));
        equal(c.getRequiredBitRate(),7.25,"foreign list bypass");
        c.getRequests().add(d);
        equal(c.getRequiredBitRate(),10.25,"foreign list mutable");
        c.setRequests(new network.TrackedRequestList());
        c.getRequests().add(a);
        equal(c.getRequiredBitRate(),13.5,"restore tracked");
        c.getRequiredBitRate();
        ByteArrayOutputStream buffer=new ByteArrayOutputStream();
        ObjectOutputStream out=new ObjectOutputStream(buffer);
        out.writeObject(c); out.close();
        ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buffer.toByteArray()));
        Circuit restored=(Circuit)in.readObject(); in.close();
        equal(restored.getRequiredBitRate(),13.5,"saved circuit");
        restored.getRequests().get(0).setRequiredBitRate(20.0);
        equal(restored.getRequiredBitRate(),20.0,"saved request mutation");
        Circuit alt=new Circuit();
        alt.getRequests().addAll(Arrays.asList(req(1.0),req(2.0),req(3.0)));
        equal(alt.getRequiredBitRate(),6.0,"addAll");
        alt.getRequests().removeIf(r -> r.getRequiredBitRate()==2.0);
        equal(alt.getRequiredBitRate(),4.0,"removeIf");
        alt.getRequests().replaceAll(r -> req(r.getRequiredBitRate()*2.0));
        equal(alt.getRequiredBitRate(),8.0,"replaceAll");
        alt.getRequests().subList(0,1).add(req(4.0));
        equal(alt.getRequiredBitRate(),12.0,"subList.add");
        java.util.ListIterator<RequestForConnection> iterator2=alt.getRequests().listIterator();
        iterator2.next(); iterator2.remove();
        equal(alt.getRequiredBitRate(),10.0,"listIterator.remove");
        Circuit cloned=alt.clone();
        equal(cloned.getRequiredBitRate(),alt.getRequiredBitRate(),"clone cache");
        System.out.println("PASS SelectiveIntegrationVerification cache de taxas e serializacao");
    }
}
