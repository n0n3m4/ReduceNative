package com.n0n3m4.mathlib;

/*
 The MIT License (MIT)

 Copyright (c) 2017 Lingaraj Sankaravelu

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.*;
import katex.hourglass.in.mathlib.R;
import org.json.JSONObject;
import org.json.JSONStringer;


/**
 * Created by lingaraj on 3/15/17.
 */

public class MathView extends WebView {
    private String TAG = "MathView";
    private static final float default_text_size = 18;
    private String display_text;
    private int text_color;
    private int bg_color;
    private int text_size;
    
     

    public MathView(Context context) {
        super(context);
        configurationSettingWebView();
        setDefaultTextColor(context);
        setDefaultTextSize();
        setDefaultBGColor(context);
    }

    public MathView(Context context, AttributeSet attrs) {
        super(context, attrs);
        configurationSettingWebView();
        TypedArray mTypeArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.MathView,
                0, 0);
        try {
            setTextColor(mTypeArray.getColor(R.styleable.MathView_setTextColor,ContextCompat.getColor(context,android.R.color.black)));
            pixelSizeConversion(mTypeArray.getDimension(R.styleable.MathView_setTextSize,default_text_size));
            setBGColor(mTypeArray.getColor(R.styleable.MathView_setBGColor,ContextCompat.getColor(context,android.R.color.white)));
        }
        catch (Exception e)
        {
            Log.d(TAG,"Exception:"+e.toString());
        }
    }

    private void pixelSizeConversion(float dimension) {
        if (dimension==default_text_size)
        {
            setTextSize((int)default_text_size);
        }
        else
        {
            int pixel_dimen_equivalent_size = (int) ((double) dimension / 1.6);
            setTextSize(pixel_dimen_equivalent_size);
        }
    }


    @SuppressLint({"SetJavaScriptEnabled", "NewApi"})
    private void configurationSettingWebView()
    {
        getSettings().setJavaScriptEnabled(true);
        getSettings().setAllowFileAccess(true);
        getSettings().setDisplayZoomControls(false);
        getSettings().setBuiltInZoomControls(false);
        getSettings().setSupportZoom(false);
        getSettings().setDomStorageEnabled(true);
        setVerticalScrollBarEnabled(false);
        getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        setWebChromeClient(new WebChromeClient()
        {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.v(TAG,cm.message() + " -- From line " + cm.lineNumber() + " of " + cm.sourceId() );
                return true;
            }
        });
    }


    public void setDisplayText(String formula_text) {
        this.display_text = formula_text;
        loadDisplayText();
    }

    final int HACK_MAXTIME = 5000;
    long listened_on = 0;
    Runnable onsize = null;
    public void listenOnSize(Runnable r)
    {
        listened_on = System.currentTimeMillis();
        onsize = r;
    }

    public void delistenOnSize()
    {
        onsize = null;
    }

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh)
    {
        super.onSizeChanged(w,h,ow,oh);
        if (listened_on + HACK_MAXTIME < System.currentTimeMillis())
            delistenOnSize();
        if (onsize != null)
            onsize.run();
    }

   private String loadKatexPage(String content)
    {
        String offline_config = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "    <head>\n" +
                "        <meta charset=\"UTF-8\">\n" +
                "        <title></title>\n" +
                "        <link rel=\"stylesheet\" type=\"text/css\" href=\"katex/katex.css\">\n" +
                // Katex.js was patched to allow wrapping of the groups
                // buildHTML_hack was affected
                "        <script type=\"text/javascript\" src=\"katex/katex.js\"></script>\n" +
                "        <script type=\"text/javascript\" src=\"katex/contrib/auto-render.min.js\"></script>\n" +
                " <style type='text/css'>"+
                "body {"+
                "margin: 0px;"+
                "padding: 0px;"+
                "font-size:" +this.text_size+"px;"+
                "color:"+getHexColor(this.text_color)+";"+
                " }"+
                " </style>"+
                "        <script>\n" +
                // Bypassing \* bug
                "          katex.__defineSymbol(\"math\", \"main\", \"bin\", \"\\u002a\", \"\\\\*\", true);\n" +
                "          function rerender()\n" +
                "          {\n" +
                "          renderMathInElement(\n" +
                "          document.getElementById('formula'),\n" +
                "          {\n" +
                "              delimiters: [\n" +
                "                  {left: \"$$\", right: \"$$\", display: true},\n" +
                "                  {left: \"\\\\[\", right: \"\\\\]\", display: true},\n" +
                "                  {left: \"$\", right: \"$\", display: false},\n" +
                "                  {left: \"\\\\(\", right: \"\\\\)\", display: false}\n" +
                "              ]\n" +
                "          });\n" +
                "          }\n" +
                "        </script>\n" +
                "    </head>\n" +
                "    <body bgcolor='{bgcolor}'><div id='formula'>{formula}</div><script>rerender();</script>\n" +
                "    </body>\n" +
                "</html>";
        return offline_config.replace("{formula}",content).replace("{bgcolor}",getHexColor(bg_color));
    }
    public void setTextSize(int size)
    {
       this.text_size = size;
    }
    public void setTextColor(int color)
    {
        this.text_color = color;
    }
    public void setBGColor(int color)
    {
        this.bg_color = color;
    }
    private String getHexColor(int intColor)
    {
        //Android and javascript color format differ javascript support Hex color, so the android color which user sets is converted to hexcolor to replicate the same in javascript.
        String hexColor = String.format("#%06X", (0xFFFFFF & intColor));
        Log.d(TAG,"Hex Color:"+hexColor);
        return hexColor;
    }
    private void setDefaultTextColor(Context context) {
        //sets default text color to black
        this.text_color = ContextCompat.getColor(context,android.R.color.black);

    }
    private void setDefaultTextSize() {
        //sets view default text size to 18
        this.text_size =(int) default_text_size;
    }
    private void setDefaultBGColor(Context context) {
        //sets view default text size to 18
        this.bg_color = ContextCompat.getColor(context,android.R.color.white);
    }

    private void loadData(String content)
    {
        this.loadDataWithBaseURL("file:///android_asset/", loadKatexPage(content),"text/html","UTF-8","about:blank");
    }

    boolean preloaded = false;
    private void loadDisplayText()
    {
        if (preloaded)
            this.loadUrl("javascript:rerender();document.getElementById('formula').textContent="+JSONObject.quote(display_text)+";rerender();null");
        else
        {
            loadData(display_text);
            preloaded=true;
        }
    }

}
