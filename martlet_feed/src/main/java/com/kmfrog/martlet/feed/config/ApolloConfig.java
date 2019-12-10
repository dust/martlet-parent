package com.kmfrog.martlet.feed.config;

import org.springframework.context.annotation.Configuration;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;



@Configuration
@EnableApolloConfig
public class ApolloConfig {

    @com.ctrip.framework.apollo.spring.annotation.ApolloConfig
    private Config config;

}
