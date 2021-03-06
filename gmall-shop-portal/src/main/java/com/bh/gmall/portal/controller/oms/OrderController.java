package com.bh.gmall.portal.controller.oms;


import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.fastjson.JSON;
import com.bh.gmall.constant.SysCacheConstant;
import com.bh.gmall.oms.service.OrderService;
import com.bh.gmall.to.CommonResult;
import com.bh.gmall.ums.entity.Member;
import com.bh.gmall.vo.order.OrderConfirmVo;
import com.bh.gmall.vo.order.OrderCreateVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@Api(tags = "订单服务")
@RequestMapping("/order")
@RestController
public class OrderController {


    @Autowired
    StringRedisTemplate redisTemplate;

    @Reference
    OrderService orderService;


    /**
     * 当信息确认完成以后下一步要提交订单。我们必须做防重复验证【接口幂等性设计】；
     * 1）、利用防重的令牌。
     * 接口幂等性设计：
     * select：
     * insert/delete/update【幂等性设计】；
     * <p>
     * 2）、数据层面怎么幂等？可用数据库的锁机制保证在数据库层面多次请求幂等
     * insert();【如果id不是自增，传入id】
     * delete();【在数据层如果带id删除，幂等操作】
     * update();【乐观锁】  update set stock=stock-1,version=version+1 where skuId=1 and version=1
     * <p>
     * 3）、业务层面；
     * 分布式锁_【令牌防重】。 order:member:1;
     * 分布式锁并发下单；
     *
     * @param accessToken
     * @return
     */
    @ApiOperation("订单确认")
    @GetMapping("/confirm")
    public CommonResult confirmOrder(@RequestParam("accessToken") String accessToken) {

        //0、检查用户是否存在
        String memberJson = redisTemplate.opsForValue().get(SysCacheConstant.LOGIN_MEMBER + accessToken);
        if (StringUtils.isEmpty(accessToken) || StringUtils.isEmpty(memberJson)) {
            CommonResult failed = new CommonResult().failed();
            failed.setMessage("用户未登录，请先登录");
            //用户未登录
            return failed;
        }

        //1、登录的用户
        Member member = JSON.parseObject(memberJson, Member.class);
        /**
         * 返回如下数据；
         * 1、当前用户的可选地址列表
         * 2、当前购物车选中的商品信息
         * 3、可用的优惠卷信息
         * 4、支付、配送、发票方式信息
         *
         *
         */
        //dubbo的RPC隐式传参；setAttachment保存一下下一个远程服务需要的参数
        RpcContext.getContext().setAttachment("accessToken", accessToken);
        //调用下一个远程服务
        OrderConfirmVo confirm = orderService.orderConfirm(member.getId());


        return new CommonResult().success(confirm);
    }


    /**
     * 创建订单的时候必须用到确认订单的那些数据
     *
     * @param totalPrice  为了比价；
     * @param accessToken
     * @return
     */
    @ApiOperation("下单")
    @PostMapping("/create")
    public CommonResult createOrder(@RequestParam("totalPrice") BigDecimal totalPrice,
                                    @RequestParam("accessToken") String accessToken,
                                    @RequestParam("addressId") Long addressId,
                                    @RequestParam(value = "note", required = false) String note,
                                    @RequestParam("orderToken") String orderToken) {


        RpcContext.getContext().setAttachment("accessToken", accessToken);
        RpcContext.getContext().setAttachment("orderToken", orderToken);
        //1、创建订单要生成订单（总额）和订单项（购物车中的商品）；

        //防重复
        OrderCreateVo orderCreateVo = orderService.createOrder(totalPrice, addressId, note);

        if (!StringUtils.isEmpty(orderCreateVo.getToken())) {
            CommonResult result = new CommonResult().failed();
            result.setMessage(orderCreateVo.getToken());
            return result;
        }
        //
        return new CommonResult().success(orderCreateVo);
    }


}
