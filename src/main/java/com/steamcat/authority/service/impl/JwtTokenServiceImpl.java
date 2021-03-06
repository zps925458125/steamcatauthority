package com.steamcat.authority.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.steamcat.authority.entity.AuthToken;
import com.steamcat.authority.entity.LoginParam;
import com.steamcat.authority.exception.AuthException;
import com.steamcat.authority.service.IJwtTokenService;
import com.steamcat.authority.utils.CookieUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName GetTokenServiceImpl
 * @Description TODO
 * @Author Administrator
 * @Data 下午 06:54
 * @Version 1.0
 **/
@Service
public class JwtTokenServiceImpl implements IJwtTokenService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenServiceImpl.class);

    private static final String GRANTTYPE = "password";

    @Value("${auth.clientId}")
    private String clientId;

    @Value("${auth.clientSecret}")
    private String clientSecret;

    @Value("${auth.loginUrl}")
    private String loginUrl;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    private static final long TIME = 86400L;

    @Value("${auth.domain}")
    private String domain;

    private static final String COOKIE_NAME = "user_token";
    @Override
    public String generateToken(LoginParam loginParam) {
        String passWord = loginParam.getPassWord();
        String userName = loginParam.getUserName();
        JSONObject jwt = null;

        try {
            jwt = restTemplate.postForObject(loginUrl, getHttpEntity(userName, passWord)
                        , JSONObject.class);
        } catch (RestClientException e) {
            LOGGER.error("call oauth2 failed, error is {}", e.getMessage());
            throw new AuthException("com.authority.1001");
        }
        // 用户身份短令牌
        String jti = jwt.get("jti").toString();
        // token串存入redis
        boolean isCache = cacheToken(jti, jwt.toJSONString());
        if (!isCache) {
            LOGGER.error("Token store redis failed");
            throw new AuthException("com.authority.1004");
        }

        saveCookie(jti);

        System.out.println(jwt.toString());
        return jti;
    }

    @Override
    public AuthToken getJwt(HttpServletRequest request) {
        // 从请求中取cookie中的token
        String jti = getJtiFromCookie(request);
        // 从redis中获得用户信息
        String jwtStr = (String) redisTemplate.opsForValue().get(jti);

        AuthToken authToken = null;
        try {
            authToken = JSONObject.parseObject(jwtStr, AuthToken.class);
            return authToken;
        } catch (Exception e) {
            LOGGER.error("token string transform entity failed, error is {}", e.getMessage());
            throw new AuthException("com.authority.1005");
        }
    }

    @Override
    public boolean loginOut(HttpServletRequest request) {
        String jti = getJtiFromCookie(request);
        Boolean isDelete = redisTemplate.delete(jti);
        // 删除cookie
        clearCookie(COOKIE_NAME);
        return true;
    }

    private String getJtiFromCookie(HttpServletRequest request) {
        Map<String, String> cookieMap = CookieUtils.getCookie(request, "user-token");
        return cookieMap.get("user-token");
    }

    private void saveCookie(String jti) {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        CookieUtils.addCookie(response, domain, "/", COOKIE_NAME, jti, -1, false);
    }

    private void clearCookie(String jti) {
        HttpServletResponse response = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getResponse();
        CookieUtils.addCookie(response, domain, "/", COOKIE_NAME, jti, 0, false);
    }

    private boolean cacheToken(String jti, String jwt) {
        redisTemplate.opsForValue().set(jti, jwt, TIME, TimeUnit.SECONDS);
        Long expire = redisTemplate.getExpire(jti, TimeUnit.SECONDS);
        return expire > 0;
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private HttpEntity getHttpEntity(String userName, String passWord) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", GRANTTYPE);
        body.add("username", userName);
        body.add("password", passWord);
        return new HttpEntity<>(body, getHeaders());
    }
}
