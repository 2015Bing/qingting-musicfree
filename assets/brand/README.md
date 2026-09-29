# 轻听 LOGO

概念：耳机环抱音符，音符上端化为一片叶子，表达轻松、安静地听音乐。主色 `#245E4F`，标志色 `#F7F8F4`；无文字，适合小尺寸桌面图标。

## 文件
- `qingting-mark.svg`：透明矢量母版，108×108 坐标，可复用。
- `qingting-logo.svg` / `qingting-logo.png`：圆角方形完整 LOGO，PNG 为 512×512。
- `qingting-circle.png`、`qingting-themed.png`、`qingting-small.png`：圆形、主题色与 48px 检查预览。
- `concepts/imagegen-concept.png`：内置 image_gen 生成的概念参考；最终 Android 图标按该概念制作成干净的原生矢量路径，未直接使用含边缘杂点的生成位图。

## Android 接入
`ic_launcher_foreground.xml` 使用与 SVG 一致的路径；108dp 画布上的标志约 51×51dp，完整落在中心安全区域。`mipmap-anydpi-v26/ic_launcher.xml` 组合墨绿底与前景；v33 资源增加 monochrome 层。Manifest 的 icon/roundIcon 都指向此自适应图标。应用版本 0.1.1 / versionCode 2。

[Android 官方图标规范](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)

预览重新生成：
```powershell
npm install --prefix .tools/icon-render @resvg/resvg-js
node tools/render-brand.cjs
```

## 生成方式与提示词
使用内置 image_gen，未使用 CLI/API fallback。初稿提示：

> Design a single finished production app logo for a minimal Chinese Android music player named Qingting (轻听). No text at all. A bold, refined original symbol combining an open circular listening/headphone loop and a gently rising musical note stem that ends as a small leaf; one cohesive fluid geometric silhouette, warm ivory #F7F8F4 only. Calm and light, contemporary editorial identity, recognizable at 32px. Flat vector-like solid fills, precise smooth edges, generous negative space, no outline hairlines, no gradient, shadow, 3D, texture, mockup, typography, border, or extra decorations. True transparent background, square PNG canvas. Center the complete ivory symbol within the central 54% of canvas width and height, keeping ample fully transparent padding on all sides; this exact padded transparent asset will be the foreground of an Android adaptive icon placed over dark forest green #245E4F. Deliver exactly one standalone logo asset, not a sheet.

细化提示：

> Refine this logo into a pristine production app icon foreground. Keep the exact basic headphone plus leaf music-note concept and centered layout. Fix every dirty/speckled/ragged/outlined edge: all shapes must be perfectly smooth, uniform solid warm ivory #F7F8F4, like clean exported SVG silhouettes. Remove ALL white speckles, tiny fragments, lines and artifacts in the negative space. No shading, texture, sketch marks, fine outlines, hairline details or gradients. Simplify the leaf to a smooth thick silhouette without a thin vein. Pure genuinely transparent background everywhere outside the ivory symbol, including between headphones and note. Keep the symbol inside the central 52% of square canvas and center it optically. One single logo foreground asset.
