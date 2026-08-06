const pptxgen = require("pptxgenjs");
const fs = require("fs");

// Colors extracted from the FeliCa Pocket Marketing template (template2.pptx)
const BLUE = "0070C0";       // primary heading / accent color used in the template
const TEAL = "0081AF";       // secondary accent (page-number badge fill)
const LIGHTBLUE = "99CCFF";  // bullet color used in template master
const RED = "FF3300";        // "Confidential" text color
const REDLINE = "FF0000";    // "Confidential" box border
const BLACK = "000000";
const WHITE = "FFFFFF";
const CARDBG = "F2F7FC";
const GRAY = "595959";

const FONT = "Meiryo UI";
const LOGO = "/tmp/claude-0/-home-user-health-connect-bridge/e3b6976a-f228-5454-b45d-1bbaf143a21b/scratchpad/pptx_work/fpm_logo.png";

function img(path) {
  return "image/png;base64," + fs.readFileSync(path).toString("base64");
}

const pres = new pptxgen();
// Match the FeliCa Pocket Marketing template's canvas: 4:3, 10in x 7.5in
pres.defineLayout({ name: "FPM_4x3", width: 10, height: 7.5 });
pres.layout = "FPM_4x3";

// Reusable footer (logo, page-number badge, copyright, staircase divider) — mirrors slideMaster1.xml
function addFooter(slide, pageNum) {
  slide.addImage({ data: img(LOGO), x: 0.196, y: 7.017, w: 1.488, h: 0.433 });

  slide.addShape(pres.ShapeType.rect, {
    x: 4.842, y: 7.249, w: 0.295, h: 0.233,
    fill: { color: TEAL }, line: { type: "none" },
  });
  slide.addText(String(pageNum), {
    x: 4.842, y: 7.249, w: 0.295, h: 0.233,
    fontFace: "Arial", fontSize: 8, color: WHITE,
    align: "center", valign: "middle", margin: 0,
  });

  slide.addText("© 2026 FeliCa Pocket Marketing Inc. All Rights Reserved.", {
    x: 6.85, y: 7.232, w: 2.95, h: 0.219,
    fontFace: "Arial", fontSize: 7, color: BLACK,
    align: "right", margin: 0,
  });

  // Staircase divider above the footer: horizontal - diagonal step - horizontal
  slide.addShape(pres.ShapeType.line, {
    x: 0, y: 6.977, w: 1.771, h: 0,
    line: { color: TEAL, width: 1.25 },
  });
  slide.addShape(pres.ShapeType.line, {
    x: 1.771, y: 6.977, w: 0.316, h: 0.236,
    line: { color: TEAL, width: 1.25 },
  });
  slide.addShape(pres.ShapeType.line, {
    x: 2.087, y: 7.213, w: 7.913, h: 0,
    line: { color: TEAL, width: 1.25 },
  });
}

// Reusable "Confidential" badge — mirrors slideMaster1.xml
function addConfidential(slide) {
  slide.addShape(pres.ShapeType.rect, {
    x: 8.780, y: 0.127, w: 1.102, h: 0.316,
    fill: { type: "none" },
    line: { color: REDLINE, width: 3 },
  });
  slide.addText("Confidential", {
    x: 8.780, y: 0.127, w: 1.102, h: 0.316,
    fontFace: "Meiryo UI", fontSize: 10.5, color: RED,
    align: "center", valign: "middle", margin: 0,
  });
}

// ---------------- Slide 1: Title (mirrors template2 slide1.xml) ----------------
{
  const slide = pres.addSlide();
  slide.background = { color: WHITE };

  slide.addText("広島市危機管理対策課　御中", {
    x: 0.279, y: 0.611, w: 7.72, h: 0.5,
    fontFace: FONT, fontSize: 18, color: BLACK, margin: 0,
  });

  slide.addText([
    { text: "防災アプリ「避難所へGo!」", options: { breakLine: true } },
    { text: "機能拡張ロードマップのご提案", options: {} },
  ], {
    x: 0.279, y: 1.387, w: 9.447, h: 1.6,
    fontFace: FONT, fontSize: 26, bold: true, color: BLUE, margin: 0,
    lineSpacingMultiple: 1.25,
  });

  slide.addText("2026.8.6", {
    x: 0.279, y: 5.324, w: 7.06, h: 0.35,
    fontFace: FONT, fontSize: 13, color: BLACK, margin: 0,
  });
  slide.addText("フェリカポケットマーケティング株式会社", {
    x: 0.279, y: 5.66, w: 7.06, h: 0.4,
    fontFace: FONT, fontSize: 15, bold: true, color: BLACK, margin: 0,
  });
}

// ---------------- Slide 2: Roadmap ----------------
{
  const slide = pres.addSlide();
  slide.background = { color: WHITE };
  addConfidential(slide);

  // Title bar — mirrors slide2.xml's "ご提案の概要" title treatment
  slide.addText("「避難所へGo!」機能拡張ロードマップ", {
    x: 0.295, y: 0.154, w: 8.0, h: 0.378,
    fontFace: FONT, fontSize: 20, bold: true, color: BLACK,
    valign: "middle", margin: 0,
  });

  // Title divider — mirrors slideMaster1.xml's full-width rule under the title
  slide.addShape(pres.ShapeType.line, {
    x: 0, y: 0.6, w: 10, h: 0,
    line: { color: BLUE, width: 2.5 },
  });

  slide.addText("広島広域都市圏ポータルアプリ連携から将来機能まで、3か年での整備計画", {
    x: 0.295, y: 0.68, w: 9.0, h: 0.3,
    fontFace: FONT, fontSize: 10.5, color: GRAY, margin: 0,
  });

  const cardY = 1.15;
  const cardH = 5.45;
  const cardW = 2.80;
  const gapW = 0.45;
  const marginX = 0.35;

  const cardX = [marginX, marginX + cardW + gapW, marginX + 2 * (cardW + gapW)];
  const arrowX = [cardX[0] + cardW, cardX[1] + cardW];

  for (const ax of arrowX) {
    slide.addShape(pres.ShapeType.rightArrow, {
      x: ax + 0.04, y: cardY + cardH / 2 - 0.16, w: gapW - 0.08, h: 0.32,
      fill: { color: TEAL }, line: { type: "none" },
    });
  }

  const phases = [
    {
      year: "令和8年度",
      sub: "ポータルアプリ連携",
      items: [
        "広島広域都市圏ポータルアプリから「避難所へGo!」へのリンク",
        "旧アプリのAS-IS機能整理・仕様確認（現行機能の棚卸し）",
        "外部連携の切り分け（パーソンファインダー／Lアラート／ハザードマップ）",
      ],
      tag: null,
    },
    {
      year: "令和9年度",
      sub: "直近の機能追加",
      items: [
        "浸水AR機能（市長指示）：GPS連動で想定浸水深度をARカメラ表示",
        "多言語対応（優先度：必須）",
        "ルート検索×ハザード情報の統合（危険箇所を回避するルート案内）",
        "小学校区ごとの避難情報通知",
        "オフライン機能の改善",
      ],
      tag: "優先度：高",
    },
    {
      year: "令和10年度",
      sub: "将来的な機能追加",
      items: [
        "避難所受付システム（QRコード／マイナンバーカード連携）",
        "スタンプラリーによる防災普及啓発",
        "内部防災システムとのAPI連携（双方向データ連携）",
        "周辺市町（廿日市市・江田島市・熊野町）への拡大対応",
      ],
      tag: null,
    },
  ];

  const headerH = 0.85;

  phases.forEach((p, i) => {
    const x = cardX[i];

    slide.addShape(pres.ShapeType.rect, {
      x, y: cardY, w: cardW, h: cardH,
      fill: { color: CARDBG }, line: { color: "D6E4F2", width: 0.75 },
    });

    slide.addShape(pres.ShapeType.rect, {
      x, y: cardY, w: cardW, h: headerH,
      fill: { color: BLUE }, line: { type: "none" },
    });

    slide.addText(p.year, {
      x: x + 0.18, y: cardY + 0.08, w: cardW - 0.3, h: 0.4,
      fontFace: FONT, fontSize: 15, bold: true, color: WHITE, margin: 0,
    });
    slide.addText(p.sub, {
      x: x + 0.18, y: cardY + 0.48, w: cardW - 0.3, h: 0.32,
      fontFace: FONT, fontSize: 10, color: "D6E9FF", margin: 0,
    });

    if (p.tag) {
      slide.addShape(pres.ShapeType.rect, {
        x: x + 0.18, y: cardY + headerH + 0.12, w: 1.05, h: 0.26,
        fill: { color: RED }, line: { type: "none" },
      });
      slide.addText(p.tag, {
        x: x + 0.18, y: cardY + headerH + 0.12, w: 1.05, h: 0.26,
        fontFace: FONT, fontSize: 9, bold: true, color: WHITE,
        align: "center", valign: "middle", margin: 0,
      });
    }

    const listY = cardY + headerH + (p.tag ? 0.48 : 0.22);
    const bodyItems = p.items.map((t) => ({
      text: t,
      options: {
        bullet: { code: "25AA", indent: 10, color: LIGHTBLUE },
        color: BLACK,
        fontSize: 9,
        fontFace: FONT,
        breakLine: true,
        paraSpaceAfter: 8,
      },
    }));

    slide.addText(bodyItems, {
      x: x + 0.18, y: listY, w: cardW - 0.34, h: cardY + cardH - listY - 0.12,
      valign: "top", margin: 0, lineSpacingMultiple: 1.05,
    });
  });

  slide.addText(
    "出典：広島市危機管理対策課 オンライン会議議事録（令和8年8月5日）を基に作成",
    { x: 0.35, y: 6.72, w: 8.5, h: 0.24, fontFace: FONT, fontSize: 8, color: GRAY, margin: 0 }
  );

  addFooter(slide, 2);
}

pres.writeFile({ fileName: "/tmp/claude-0/-home-user-health-connect-bridge/e3b6976a-f228-5454-b45d-1bbaf143a21b/scratchpad/roadmap_fpm.pptx" })
  .then(() => console.log("done"));
