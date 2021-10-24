# 스프링 시큐리티와 OAuth 2.0으로 로그인 기능 구현하기📌


## 구글 서비스 등록
발급된 인증 정보(clientId, clientSecret)를 통해서 로그인 기능과 소셜 서비스 기능을 사용할 수 있으니 무조건 발급받고 시작해야한다.

## 구글 로그인 연동하기

**사용자 정보를 담당할 도메인 User클래스**
```java
package com.jojoldu.book.springboot.domain.user;

import com.jojoldu.book.springboot.domain.BaseTimeEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@NoArgsConstructor
@Entity
public class User extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column
    private String picture;

    @Enumerated(EnumType.STRING)
    //JPA로 데이터베이스로 저장할때 Enum값을 어떤 형태로 저장할지를 결정. 기본적으로 int로 된 숫자가 저장되는데 숫자로 저장되면 데이터베이스로 확인할때 그 값이 무슨 코드를 의미하는지 알 수가 없음.
    //그래서 문자열(EnumType.STRING)로 저장될 수 있도록 선언.
    @Column(nullable = false)
    private Role role;

    @Builder
    public User(String name, String email, String picture, Role role){
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.role = role;
    }

    public User update(String name, String picture){
        this.name = name;
        this.picture = picture;

        return this;
    }
    public String getRoleKey(){
        return this.role.getKey();

    }

}

```
**각 사용자의 권한을 관리할 Enum 클래스 Role**
```java
package com.jojoldu.book.springboot.domain.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    GUEST("ROLE_GUEST", "손님"),
    USER("ROLE_USER", "일반 사용자");

    private final String key;
    private final String title;


}

```

**User의 CRUD를 책임질 UserRepository**
```java
package com.jojoldu.book.springboot.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    //소셜 로그인으로 반환되는 값 중 email을 통해 이미 생성된 사용자인지 처음 가입하는 사용자인지 판단하기 위한 메소드.

}

```

**스프링 시큐리티 설정**
```java
package com.jojoldu.book.springboot.config.auth;

import com.jojoldu.book.springboot.domain.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@RequiredArgsConstructor
@EnableWebSecurity //Spring Security 설정들을 활성화
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final CustomOAuth2UserService customOAuth2UserService;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .headers().frameOptions().disable() //h2-console 화면을 사용하기 위해 해당 옵션들을 disable
                .and()
                .authorizeRequests()
                .antMatchers("/", "/css/**", "/images/**", "/js/**", "/h2-console/**", "/profile").permitAll()
                .antMatchers("/api/v1/**").hasRole(Role.USER.name())
                .anyRequest().authenticated()
                .and()
                .logout()
                .logoutSuccessUrl("/")
                .and()
                .oauth2Login() //OAuth 2 로그인 기능에 대한 여러 설정의 진입점
                .userInfoEndpoint() //OAuth 2 로그인 성공 이후 사용자 정보를 가져올때의 설정들을 담당
                .userService(customOAuth2UserService);
                //소셜 로그인 성공 시 후속 조치를 진행할 UserService 인터페이스의 구현체를 등록
                //리소스 서버(즉, 소셜서비스들) 에서 사용자 정보를 가져온 상태에서 추가로 진행하고자 하는 기능을 명시할수 있음
    }
}
```

**CustomOAuth2UserService**<br>
구글 로그인 이후 가져온 사용자의 정보(email, name, picture 등)들을 기반으로 가입 및 정보 수정, 세션 저장 등의 기능을 지원

```java
package com.jojoldu.book.springboot.config.auth;

import com.jojoldu.book.springboot.config.auth.dto.OAuthAttributes;
import com.jojoldu.book.springboot.config.auth.dto.SessionUser;
import com.jojoldu.book.springboot.domain.user.User;
import com.jojoldu.book.springboot.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Collections;

//구글 로그인 이후 가져온 사용자의 정보(email, name, picture 등)들을 기반으로 가입 및 정보수정, 세션 저장등의 기능을 지원

@RequiredArgsConstructor
@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final UserRepository userRepository;
    private final HttpSession httpSession;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2UserService delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        //현재 로그인 진행중인 서비스를 구분하는 코드
        String userNameAttributeName = userRequest.getClientRegistration().getProviderDetails()
                .getUserInfoEndpoint().getUserNameAttributeName();
                //OAuth2 로그인 진행 시 키가 되는 필드값을 이야기함. Primary Key와 같은 의미.

        //OAuth2UserService를 통해 가져온 OAuth2User의 attributes를 OAuthAttributes클래스에 담음
        OAuthAttributes attributes = OAuthAttributes.of(registrationId, userNameAttributeName, oAuth2User.getAttributes());

        User user = saveOrUpdate(attributes);
        //세션에 사용자 정보를 저장
        httpSession.setAttribute("user", new SessionUser(user));

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority(user.getRoleKey())),
                attributes.getAttributes(),
                attributes.getNameAttributeKey());
    }


    private User saveOrUpdate(OAuthAttributes attributes) {
        User user = userRepository.findByEmail(attributes.getEmail())
                .map(entity -> entity.update(attributes.getName(), attributes.getPicture())) //사용자 정보 업데이트
                .orElse(attributes.toEntity()); //처음 가입

        return userRepository.save(user);
    }
}

```

**OAuthAttributes**
```java
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
        return ofGoogle(userNameAttributeName, attributes);
    }
    private static OAuthAttributes ofGoogle(String userNameAttributeName,
                                             Map<String, Object> attributes){
        return OAuthAttributes.builder()
                .name((String) attributes.get("name"))
                .email((String) attributes.get("email"))
                .picture((String) attributes.get("picture"))
                .attributes(attributes)
                .nameAttributeKey(userNameAttributeName)
                .build();

    }

    //User 엔티티를 생성
    //OAuthAttributes에서 엔티티를 생성하는 시점은 처음 가입할 때
    //가입할때의 기본 권한을 GUEST로 주기 위해서 role 빌더값에는 Role.GUEST를 사용

    public User toEntity(){
        return User.builder()
                .name(name)
                .email(email)
                .picture(picture)
                .role(Role.GUEST)
                .build();
    }


}

```

**SessionUser**<br>
User클래스에 직렬화 코드를 넣고 User클래스를 세션에 저장하는데 사용하는것에는 생각해야 할 것들이 많다.<br>
이유는 User클래스가 엔티티 이기 때문. 엔티티 클래스에는 언제 다른 엔티티와 관계가 형성될지 모른다.<br>
예를 들어 @OneToMany, @ManyToMany 등 자식 엔티티를 갖고 있다면 직렬화 대상에 자식들 까지도 포함되어 성능이슈, 부수 효과가 발생할 확률이 높다.<br>
그래서 직렬화 기능을 가진 세션 Dto를 하나 추가로 만드는것이 이후 운영 및 유지보수 때 많은 도움이 된다.


```java
package com.jojoldu.book.springboot.config.auth.dto;

import com.jojoldu.book.springboot.domain.user.User;
import lombok.Getter;

import java.io.Serializable;

@Getter
public class SessionUser implements Serializable {
    private String name;
    private String email;
    private String picture;

    public SessionUser(User user){
        this.name = user.getName();
        this.email = user.getEmail();
        this.picture = user.getPicture();
    }

}

```

## 로그인 테스트
**index.mustache**
```html
{{>layout/header}}


<h1>스프링부트로 시작하는 웹 서비스</h1>
<div class="col-md-12">
    <div class="row">
        <div class="col-md-6">
            <a href="/posts/save" role="button" class="btn btn-primary">글 등록</a>
            {{#userName}}
                Logged in as: <span id="user">{{userName}}</span>
                <a href="/logout" class="btn btn-info active" role="button">Logout</a>
            {{/userName}}
            {{^userName}}
                <a href="/oauth2/authorization/google" class="btn btn-success active" role="button">Google Login</a>
                <a href="/oauth2/authorization/naver" class="btn btn-secondary active" role="button">Naver Login</a>
            {{/userName}}
        </div>
    </div>
    <br>
    <!-- 목록 출력 영역 -->
    <table class="table table-horizontal table-bordered">
        <thead class="thead-strong">
        <tr>
            <th>게시글번호</th>
            <th>제목</th>
            <th>작성자</th>
            <th>최종수정일</th>
        </tr>
        </thead>
        <tbody id="tbody">
        {{#posts}}
            <tr>
                <td>{{id}}</td>
                <td><a href="/posts/update/{{id}}">{{title}}</a></td>
                <td>{{author}}</td>
                <td>{{modifiedDate}}</td>
            </tr>
        {{/posts}}
        </tbody>
    </table>
</div>
{{>layout/footer}}


```
  /logout은 스프링 시큐리티에서 기본적으로 제공하는 로그아웃 URL이므로 별도로 컨트롤러를 만들필요가 없다.<BR>
  /oauth2/authorization/google 또한 스프링 시큐리티에서 기본적으로 제공하는 로그인 URL이므로 별도로 컨트롤러를 만들필요가 없다.

**IndexController**<br>
index.mustache에서 userName을 사용할 수 있게 IndexController에서 userName을 model에 저장하는 코드 추가

```java
private final HttpSession httpSession;

    @GetMapping("/")
    public String index(Model model){
        model.addAttribute("posts", postsService.findAllDesc());

        SessionUser user = (SessionUser) httpSession.getAttribute("user");
        if(user != null){
            model.addAttribute("userName", user.getName());
        }
        return "index";

    }
```

## 어노테이션 기반으로 개선
```java
        SessionUser user = (SessionUser) httpSession.getAttribute("user");
```
위의 세션값을 가져오는 코드는 다른 컨트롤러에서 세션값이 필요하면 그때마다 같은 코드를 반복해서 넣어주어야 한다.  <br>이 부분을 메소드 인자로 세션값을 바로 받을수 있도록 변경.

**LoginUser 어노테이션**
```java
package com.jojoldu.book.springboot.config.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER) //이 어노테이션이 생성될 수 있는 위치를 저장
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {
}

```

**LoginUserArgumentResolver**<br>
HandlerMethodArgumentResolver 인터페이스를 구현한 클래스<br>
HandlerMethodArgumentResolver는 조건에 맞는 경우 HandlerMethodArgumentResolver의 구현체가
지정한 값을 해당 메소드의 파라미터로 넘길수 있음

```java
package com.jojoldu.book.springboot.config.auth;

import com.jojoldu.book.springboot.config.auth.LoginUser;
import com.jojoldu.book.springboot.config.auth.dto.SessionUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpSession;

@RequiredArgsConstructor
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final HttpSession httpSession;

    //컨트롤러 메서드의 특정 파라미터를 지원하는지 판단
    //여기서는 파라미터에 @LoginUser 어노테이션이 붙어 있고, 파라미터 클래스 타입이 SessionUser.class인 경우 true를 반환
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean isLoginUserAnnotation = parameter.getParameterAnnotation(LoginUser.class) != null;
        boolean isUserclass = SessionUser.class.equals(parameter.getParameterType());

        return isLoginUserAnnotation && isUserclass;
    }

    //파라미터에 전달할 객체를 생성
    //여기서는 세션에서 객체를 가져옴

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        return httpSession.getAttribute("user");
    }
}

```

**WebConfig** <br>
LoginUserArgumentResolver가 스프링에서 인식될 수 있도록 WebMvcConfigurer에 추가
```java
package com.jojoldu.book.springboot.config;

import com.jojoldu.book.springboot.config.auth.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration

public class WebConfig implements WebMvcConfigurer {
    private final LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(loginUserArgumentResolver);
    }
}

```

**indexController 개선**<br>
이제는 어떤 컨트롤러든지 @LoginUser만 사용하면 세션 정보를 가져올수 있음
```java
@GetMapping("/")
    public String index(Model model, @LoginUser SessionUser user){
        model.addAttribute("posts", postsService.findAllDesc());

        if(user != null){
            model.addAttribute("userName", user.getName());
        }
        return "index";

    }
```

## 세션 저장소로 데이터베이스 사용하기
세션은 실행되는 WAS의 메모리에서 저장되고 호출되기 때문에 내장톰캣처럼 애플리케이션 실행 시 실행 되는 구조에선 항상 초기화가 된다.
그래서 현재 우리가 만든 서비스는 애플리케이션을 재실행하면 로그인이 풀린다.
또 한가지 문제는 2대 이상의 서버에서 서비스 하고 있다면 톰캣마다 세션 동기화 설정을 해야만 한다. 그래서 현업에서는 세션 저장소에 대해 다음의 3가지중 한 가지를 선택한다.

  1. 톰캣 세션을 사용<BR>
  * 별다른 설정을 하지 않을때 기본적으로 선택되는 방식<BR>
  * 이렇게 될 경우 톰켓에 세션이 저장되기 때문에 2대 이상의 WAS가 구동되는 환경에서는 톰캣들 간의 세션공유를 위한 추가 설정이 필요

  2. MySQL과 같은 데이터베이스를 세션 저장소로 사용
  * 여러 WAS간의 공용 세션을 사용할 수 있는 가장 쉬운 방법
  * 많은 설정이 필요 없지만, 결국 로그인 요청마다 DB IO가 발생하여 성능상 이슈가 발생할 수 있음
  * 보통 로그인 요청이 많이 없는 백오피스, 사내 시스템 용도에서 사용
  3. Redis, Memcached와 같은 메모리 DB를 세션 저장소로 사용
  * B2C 서비스에서 가장 많이 사용하는 방식
  * 실제 서비스로 사용하기 위해서는 Embedded Redis와 같은 방식이 아닌 외부 메모리 서버가 필요
