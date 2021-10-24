# 네이버 로그인 연동📌

## 네이버 API 등록

## application-oauth.properties 등록
네이버에서는 스프링 시큐리티를 공식 지원하지 않기 때문에 그동안 Common-OAuth2Provider에서 해주던 값들도 전부 수동으로 입력해야 한다.

**application-oauth.properties**
```
//구글
spring.security.oauth2.client.registration.google.client-id=test
spring.security.oauth2.client.registration.google.client-secret=test
spring.security.oauth2.client.registration.google.scope=profile,email

//네이버
# registration
spring.security.oauth2.client.registration.naver.client-id=test
spring.security.oauth2.client.registration.naver.client-secret=test
spring.security.oauth2.client.registration.naver.redirect-uri={baseUrl}/{action}/oauth2/code/{registrationId}
spring.security.oauth2.client.registration.naver.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.naver.scope=name,email,profile_image
spring.security.oauth2.client.registration.naver.client-name=Naver

# provider
spring.security.oauth2.client.provider.naver.authorization-uri=https://nid.naver.com/oauth2.0/authorize
spring.security.oauth2.client.provider.naver.token-uri=https://nid.naver.com/oauth2.0/token
spring.security.oauth2.client.provider.naver.user-info-uri=https://openapi.naver.com/v1/nid/me
spring.security.oauth2.client.provider.naver.user-name-attribute=response //기준이 되는 user_name의 이름을 네이버에서는 response로 해야함. 이유는 네이버의 회원 조회 시 반환이 JSON형태이기 때문.
```

## 스프링 시큐리티 설정 등록

**OAuthAttributes**
```
package com.jojoldu.book.springboot.config.auth.dto;

import com.jojoldu.book.springboot.domain.user.Role;
import com.jojoldu.book.springboot.domain.user.User;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
public class OAuthAttributes {
    private Map<String, Object> attributes;
    private String nameAttributeKey;
    //OAuteh2 로그인 진행 시 키가 되는 필드값, Primary Key와 같은 의미
    //구글의 경우 기본적으로 코드를 지원("sub")하지만, 네이버 카카오 등은 기본 지원하지 않는다.
    private String name;
    private String email;
    private String picture;

    @Builder
    public OAuthAttributes(Map<String, Object> attributes,
                           String nameAttributeKey, String name,
                           String email, String picture){
        this.attributes = attributes;
        this.nameAttributeKey = nameAttributeKey;
        this.name = name;
        this.email = email;
        this.picture = picture;
    }

    //OAuth2User에서 반환하는 사용자 정보는 Map이기 때문에 값 하나하나를 변환해야만 함
    public static OAuthAttributes of(String registrationId,
                                     String userNameAttributeName,
                                     Map<String, Object> attributes){
        if("naver".equals(registrationId)){
            return ofNaver("id", attributes);
        }
        return ofGoogle(userNameAttributeName, attributes);
    }
    private static OAuthAttributes ofNaver(String userNameAttributeName, Map<String, Object> attributes){
        Map<String, Object> response = (Map<String, Object>) attributes.get("response");

        return OAuthAttributes.builder()
                .name((String) response.get("name"))
                .email((String) response.get("email"))
                .picture((String) response.get("profile_image"))
                .attributes(response)
                .nameAttributeKey(userNameAttributeName)
                .build();
    }

}

```

## 기존 테스트에 시큐리티 적용하기
기존 테스트에 시큐리티 적용으로 문제가 되는 부분들을 해결

### 문제1. CustomOAuth2UserService를 찾을 수 없음<br>
"hello가_리턴된다" 테스트의 메시지를 보면 **"No qulifying bean of type 'com.jojoldu.book.springboot.config.auth.CustomOAuth2UserService'"** 라는 메시지가 등장함.<br>
이는 CustomOAuth2UserService를 생성하는데 필요한 소셜 로그인 관련 설정값들이 없기 때문에 발생한다.<br>
하지만 우리는 분명 application-oauth.properties로 추가하였음.<br>
-> src/main 환경과 src/test 환경의 차이 때문. <br>src/main/resources/application.properties가 테스트 코드를 수행할때도 적용되는 이유는 test에 application.properties가 없으면 main의 설정을 그대로 가져오기 때문.
다만 자동으로 가져오는 옵션의 범위는 application.properties 파일까지이다.<br>
즉, application-oauth.properties는 test에 파일이 없다고 가져오는 파일이 아니라는것.



**application.properties**<br>
테스트 환경을 위한 application.properties를 만듦

```
spring.jpa.show_sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect
spring.h2.console.enabled=true
spring.session.store-type=jdbc

# Test OAuth
spring.security.oauth2.client.registration.google.client-id=test
spring.security.oauth2.client.registration.google.client-secret=test
spring.security.oauth2.client.registration.google.scope=profile,email

```

### 문제2. 302 Status Code<br>
"Posts_등록된다" 테스트 로그를 보면 302가 반환되기 떄문에 테스트가 실패했다.<br>
이는 스프링 시큐리티 설정 때문에 인증되지 않은 사용자의 요청은 이동시키기 때문이다.<br>
그래서 이런 API요청은 임의로 인증된 사용자를 추가하여 테스트 하면 된다.

스프링 시큐리티에서 공식적으로 지원하는 방법을 사용 하면 된다.

**build.gradle**<br>
스프링 시큐리티 테스트를 위한 여러 도구를 지원하는 spring-security-test를 build.gradle에 추가
```
compile('org.springframework.security:spring-security-test')
```

**PostApiControllerTest**<br>
PostApiControllerTest의 2개 테스트 메소드에 임의의 사용자 인증을 추가


```
@Test
@WithMockUser(roles="USER")
//인증된 가짜 사용자를 만들어 사용
//roles에 권한을 추가할 수 있다
//즉, 이 어노테이션으로 인해 ROLE_USER 권한을 가진 사용자가 API를 요청하는 것과 동일한 효과

public void Posts_수정된다() throws Exception{}

@Test
@WithMockUser(roles="USER")
public void Posts_등록된다() throws Exception{}

```
@WithMockUser는 MockMvc에서만 작동하고 현재 PostApiControllerTest는 MockMvc를 전혀 사용하지 않기 때문에 테스트가 성공하지 않는다.<br>
그래서 MockMvc를 추가한다.

**PostApiControllerTest**
```
package com.jojoldu.book.springboot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jojoldu.book.springboot.domain.posts.Posts;
import com.jojoldu.book.springboot.domain.posts.PostsRepository;
import com.jojoldu.book.springboot.web.dto.PostsSaveRequestDto;
import com.jojoldu.book.springboot.web.dto.PostsUpdateRequestDto;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment =  SpringBootTest.WebEnvironment.RANDOM_PORT)

public class PostsApiControllerTest {
    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @Before
    public void setup(){
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }


    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PostsRepository postsRepository;

    @After
    public void tearDown() throws Exception{
        postsRepository.deleteAll();
    }

    @Test
    @WithMockUser(roles="USER")
    public void Posts_수정된다() throws Exception{
        //given
        Posts savePosts = postsRepository.save(Posts.builder()
                .title("title")
                .content("content")
                .author("author")
                .build());
        Long updateId = savePosts.getId();
        String expectedTitle = "title2";
        String expectedContent = "content2";

        PostsUpdateRequestDto requestDto = PostsUpdateRequestDto.builder()
                .title(expectedTitle)
                .content(expectedContent)
                .build();
        String url = "http://localhost:" + port + "/api/v1/posts/" + updateId;

        HttpEntity<PostsUpdateRequestDto> requestEntity = new HttpEntity<>(requestDto);


        //when
        mvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(new ObjectMapper().writeValueAsString(requestDto)))
                //body영역은 문자열로 표현하기 위해 ObjectMapper를 통해 문자열 JSON으로 변환
                .andExpect(status().isOk());
        //then
        List<Posts> all = postsRepository.findAll();
        assertThat(all.get(0).getTitle()).isEqualTo(expectedTitle);
        assertThat(all.get(0).getContent()).isEqualTo(expectedContent);

    }

    @Test
    @WithMockUser(roles="USER")
    public void Posts_등록된다() throws Exception{
        //given
        String title = "title";
        String content = "content";
        PostsSaveRequestDto requestDto = PostsSaveRequestDto.builder()
                .title(title)
                .content(content)
                .author("author")
                .build();
        String url = "http://localhost:" + port + "/api/v1/posts";

        //when
        mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .content(new ObjectMapper().writeValueAsString(requestDto)))
                .andExpect(status().isOk());
        //then
        List<Posts> all = postsRepository.findAll();
        assertThat(all.get(0).getTitle()).isEqualTo(title);
        assertThat(all.get(0).getContent()).isEqualTo(content);
    }




}

```

### 문제3. @WebMvcTest에서 CustomOAuth2UserService를 찾을수가 없음<br>
"hello가_리턴된다" 테스트를 확인해보면 첫 번째로 해결한 것과 동일한 메시지인 **"No qualifying bean of type 'com.jojoldu.book.springboot.config.auth.CustomOAuth2UserService'"** 가 나온다.<br>
1번을 통해 스프링 시큐리티 설정은 잘 작동했지만, @WebMvcTest는 CustomOAuth2UserService를 스캔하지 않았기 때문.<br>
@WebMvcTest는 WebSecurityConfigurerAdapter, WebMvcConfigurer를 비롯한 @ControllerAdvice, @Controller를 읽는다.<br>
즉, @Repository, @Service, @Component는 스캔 대상이 아니다.<br>
그러니 SecurityConfig는 읽었지만, SecurityConfig를 생성하기 위해 필요한 CustomOAuth2UserService는 읽을수가 없어 에러가 발생한것.<br>
이 문제를 해결하기 위해 스캔 대상에서 SecurityConfig를 제거한다.

**HelloControllerTest**
```
@WebMvcTest(controllers = HelloController.class,
        excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        }
)
```

그리고 @WithMockUser를 사용해서 가짜로 인증된 사용자를 생성
```
@Autowired
private MockMvc mvc;

@Test
@WithMockUser(roles="USER")
public void hello가_리턴된다() throws Exception{}

@Test
public void helloDto가_리턴된다() throws Exception{}
```

이렇게 한 뒤 다시 테스트를 돌려보면 다음과 같은 추가 에러가 발생한다.
**java.lang.IllegalArgumentException: At least one JPA metamodel must be present!**

이 에러는 @EnableJpaAuditing으로 인해 발생한다.<br>
@EnableJpaAuditing을 사용하기 위해선 최소 하나의 @Entity클래스가 필요하다.<br>
@WebMvcTest이다 보니 당연히 없고, @EnableJpaAuditing가 @SpringBootApplication와 함께 있다보니 @WebMvcTest에서도 스캔하게 된것.<br>
그래서 @EnableJpaAuditing과 @SpringBootApplication 둘을 분리한다.

**Application**
```
package com.jojoldu.book.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

//@EnableJpaAuditing //삭제
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

```

**JpaConfig**
```
package com.jojoldu.book.springboot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;


@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

```

@WebMvcTest는 일반적인 @Configuration은 스캔하지 않기 때문에 에러가 발생하지 않는다.
