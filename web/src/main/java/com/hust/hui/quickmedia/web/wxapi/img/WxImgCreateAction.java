package com.hust.hui.quickmedia.web.wxapi.img;

import com.hust.hui.quickmedia.common.image.ImgCreateOptions;
import com.hust.hui.quickmedia.common.image.ImgCreateWrapper;
import com.hust.hui.quickmedia.common.tools.ChineseDataExTool;
import com.hust.hui.quickmedia.common.util.FileUtil;
import com.hust.hui.quickmedia.common.util.FontUtil;
import com.hust.hui.quickmedia.web.entity.ResponseWrapper;
import com.hust.hui.quickmedia.web.entity.Status;
import com.hust.hui.quickmedia.web.wxapi.WxBaseResponse;
import com.hust.hui.quickmedia.web.wxapi.common.WxImgCreateTemplateEnum;
import com.hust.hui.quickmedia.web.wxapi.helper.ImgGenHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Created by yihui on 2017/9/18.
 */
@RestController
@Slf4j
public class WxImgCreateAction {

    private static final int DEFAULT_SIZE = 640;
    private static final int DEFAULT_SMALL_SIZE = 400;

    private static final int LINE_PADDING = 10;
    private static final int TOP_PADDING = 20;
    private static final int BOTTOM_PADDING = 20;
    private static final int LEFT_PADDING = 20;
    private static final int RIGHT_PADDING = 20;


    @RequestMapping(value = "/wx/create", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
    public ResponseWrapper<WxBaseResponse> create(HttpServletRequest httpServletRequest, WxImgCreateRequest wxImgCreateRequest) {

        BufferedImage bfImg = getImg(httpServletRequest);

        WxImgCreateTemplateEnum cenum = WxImgCreateTemplateEnum.getEnum(wxImgCreateRequest.getTemplateId());
        if (cenum == null) {
            throw new IllegalArgumentException("非法的模板id!");
        }


        int paddingSize, imgSize;
        if (cenum.getDrawStyle() == ImgCreateOptions.DrawStyle.HORIZONTAL) {
            paddingSize = LEFT_PADDING + RIGHT_PADDING;
            imgSize = bfImg.getWidth();
        } else {
            paddingSize = TOP_PADDING + BOTTOM_PADDING;
            imgSize = bfImg.getHeight();
        }
        int size = calculateSize(wxImgCreateRequest.getMsg(), paddingSize, imgSize);
        ImgCreateWrapper.Builder builder = ImgCreateWrapper.build()
                .setImgW(size)
                .setImgH(size)
                .setDrawStyle(cenum.getDrawStyle())
                .setFont(FontUtil.BIG_DEFAULT_FONT)
                .setLeftPadding(LEFT_PADDING)
                .setRightPadding(RIGHT_PADDING)
                .setTopPadding(TOP_PADDING)
                .setBottomPadding(BOTTOM_PADDING)
                .setLinePadding(LINE_PADDING)
                .setBorderLeftPadding(18)
                .setBorderTopPadding(20)
                .setBorderBottomPadding(20)
                .setBorder(true);

        try {
            BufferedImage ans;
            if (cenum.imgEnd()) {
                ans = builder.setAlignStyle(cenum.getAlignStyle())
                        .drawContent(wxImgCreateRequest.getMsg())
                        .setFont(FontUtil.getFont("font/txlove.ttf", Font.ITALIC, 16))
                        .setAlignStyle(cenum.getDrawStyle() == ImgCreateOptions.DrawStyle.HORIZONTAL ? ImgCreateOptions.AlignStyle.RIGHT : ImgCreateOptions.AlignStyle.BOTTOM)
                        .drawContent(ChineseDataExTool.getNowLunarDate())
                        .drawContent(" ")
                        .setAlignStyle(ImgCreateOptions.AlignStyle.CENTER)
                        .drawImage(bfImg)
                        .asImage()
                ;
            } else {
                ans = builder.setAlignStyle(ImgCreateOptions.AlignStyle.CENTER)
                        .drawImage(bfImg)
                        .drawContent(" ")
                        .setAlignStyle(cenum.getAlignStyle())
                        .drawContent(wxImgCreateRequest.getMsg())
                        .setFont(FontUtil.getFont("font/txlove.ttf", Font.ITALIC, 16))
                        .setAlignStyle(cenum.getDrawStyle() == ImgCreateOptions.DrawStyle.HORIZONTAL ? ImgCreateOptions.AlignStyle.RIGHT : ImgCreateOptions.AlignStyle.BOTTOM)
                        .drawContent(ChineseDataExTool.getNowLunarDate())
                        .asImage()
                ;
            }

            WxBaseResponse wxBaseResponse = new WxBaseResponse();
//            wxBaseResponse.setBase64result(Base64Util.encode(ans, MediaType.ImagePng.getExt()));
//            wxBaseResponse.setPrefix(MediaType.ImagePng.getPrefix());
            wxBaseResponse.setImg(save(ans));
            return ResponseWrapper.successReturn(wxBaseResponse);
        } catch (Exception e) {
            log.error("WxImgCreateAction!Create image error! e: {}", e);
            return ResponseWrapper.errorReturn(Status.StatusEnum.FAIL);
        }
    }


    private BufferedImage getImg(HttpServletRequest request) {
        MultipartFile file = null;
        if (request instanceof MultipartHttpServletRequest) {
            file = ((MultipartHttpServletRequest) request).getFile("image");
        }

        if (file == null) {
            throw new IllegalArgumentException("图片不能为空!");
        }


        // todo 图片类型校验, 目前只支持 jpg, png, webp 等静态图片格式
        String contentType = file.getContentType();


        // 获取BufferedImage对象
        try {
            BufferedImage bfImg = ImageIO.read(file.getInputStream());
            return bfImg;
        } catch (IOException e) {
            log.error("WxImgCreateAction!Parse img from httpRequest to BuferedImage error! e: {}", e);
            throw new IllegalArgumentException("不支持的图片类型!");
        }
    }


    private int calculateSize(String msg, int paddingSize, int imgSize) {
        String[] strs = StringUtils.split(msg, "\n");
        int maxLen = 0;
        for (String str : strs) {
            if (str.length() > maxLen) {
                maxLen = str.length();
            }
        }


        // 最长的一行文字长度
        int size = FontUtil.BIG_DEFAULT_FONT.getSize() * (maxLen + 3) + paddingSize;


        // 不能超过默认的长度
        size = Math.min(size, DEFAULT_SIZE);


        // 不能比最小的还要小
        int tmpSize = Math.max(size, DEFAULT_SMALL_SIZE);

        imgSize += paddingSize;
        if (imgSize > size || imgSize  < tmpSize) {
            return tmpSize;
        } else {
            return imgSize;
        }
    }


    private static String save(BufferedImage bf) {
        String path = ImgGenHelper.genTmpImg("png");
        try {
            File file = new File(ImgGenHelper.TMP_PATH + path);
            FileUtil.mkDir(file);
            ImageIO.write(bf, "png", file);
        } catch (Exception e) {
            log.error("save file error!");
        }
        return path;
    }
}
