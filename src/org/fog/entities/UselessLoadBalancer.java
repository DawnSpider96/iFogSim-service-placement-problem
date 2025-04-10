package org.fog.entities;

import org.fog.utils.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Joseph Poon
 * For implementations of controllerComponent where service discovery entries are uniquely identifiable.
 * ie getDestinationDeviceId is NOT called.
 */
public class UselessLoadBalancer implements LoadBalancer {
    protected Map<String, Integer> loadBalancerPosition = new HashMap();
    public int getDeviceId(String microservice, ServiceDiscovery serviceDiscoveryInfo) {
        throw new NullPointerException("CRITICAL ERROR: Load Balancer should NOT be used!");
//        return -1;
    }
}
