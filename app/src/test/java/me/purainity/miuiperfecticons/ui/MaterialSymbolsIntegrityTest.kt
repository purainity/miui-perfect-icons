package me.purainity.miuiperfecticons.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class MaterialSymbolsIntegrityTest {
    @Test
    fun listedIconsMatchOfficialAssets() {
        // 校验 Google 官方 Material Symbols Outlined 24px 的原始资源
        // 以下新增图标来自 google/material-design-icons 的 10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7。
        val expected = mapOf(
            "ic_delete_sweep" to "4288ae4bc24c88073ff231a47b41b50365c9bb1fd52d03d6da1209a43f01ab74",
            "ic_docs" to "56ae3941e206cb4dc3a899a4e1fe886a5279420bc48c33d94263278642ad6a8a",
            "ic_preview" to "18cc9d7c1ffb3caa25c7c704d986c1867f28fbbfbb9a1ad30dfc9bce5bcaf41a",
            "ic_app_registration" to "c19ca220e4a7fd3bdf719b52276415641c2a0c6b785e83d993fb52e54367af81",
            "ic_animation" to "aa66cfccf80ac4009a17204b10700f22b3697399ef524e848798dbff7acb0c73",
            "ic_select_all" to "e793d7e7d8f702194714d247d044ebfbddba366137d1a6c0b3c929d8f3ef0093",
            "ic_remove_selection" to "318a270e6a3217d91025bdc21c8dabef7b27ee31ea82330a0bffc897f80f0135",
            "ic_add_photo_alternate" to "0df5323becbefa90c53bfbc68e2f86040b83ce14a325d12f4aab59abfcc90db9",
            "ic_create_new_folder" to "9835ac36866e23213661c1796fa5af2d200a63af3f9c5b251afb5d7b16735658",
            "ic_archive" to "e27698bcdb3609b38f5c398a844fb1bfd71af8eb43851afff38635b198201054",
            "ic_inventory_2" to "1bf70c5dc8432c6d8d0e23345e8d6aa1426c29964fd28b9734b0576e28b33701",
            "ic_back" to "cd1f5a1109c07c79ac3e52a9a1ae8ab14be7a3bff7f7bc3559f7088d60eca3aa",
            "ic_chevron" to "d3f53b271fdb2150775b4252bf46139c3ab5ca872802372a1089e6e1dd6f2a35",
            "ic_close" to "31644025d94b5a5637d75660ebdcb93c398f2620bfc12f7a7a9362965e271288",
            "ic_download" to "75f8b4dae0ef464f527f3adadde29a0d57e9875985d49f44df3585f60fe9fbf4",
            "ic_extension" to "bc0fec4d13b0f4e71c3ac6209c5f323932f8f6011d8eb181980b7cc0511670bb",
            "ic_external" to "12b1dfc1be3b858a2d22781dcae341ec180834266198022b2f63494f7aab9c04",
            "ic_folder" to "527c6a7dd741753f91cd8f33894aa2cfac375138c852b1ebfc2b43195ebaa313",
            "ic_history" to "810cd1d657367d5f33c56bde96d6b3cb0e714528b8d60fb548a96cd4a63d257e",
            "ic_layers" to "862fbc5a61cd285db27fc455aa7578a669a71c8b0bbf50df1220d12a097677e5",
            "ic_link" to "c1aad36fbc2f5857731304472eb30c26b81b385e05852be9d3f6575848b1f35e",
            "ic_palette" to "77da392d239862f1c1a4f736d698debcffe9477eacb49947b41210360af23b92",
            "ic_search" to "e99d075e9867ec5347fc850eb629a6bbef1819cf393c39eaa06b43251f96ecd3",
            "ic_person" to "ab409acf8825d92c92928e06d2d84c003f9e227033594443d0ba5700fe2fe072",
            "ic_info" to "b105df7f824e7eafbd0a73ebc55085134409161d606894c97685ba1cd38cbcc2",
            "ic_privacy" to "071c15457d0743e2820202cbf46dc214709f4faef33c909fe505e42edad56808",
        )
        // 品牌图标取自 Apache 2.0 许可的 Remix Icon v4.6.0
        val remix = mapOf(
            "ic_github" to "9febde5d600c1f4ae09f8324b3e7ca9762cd37085a1b6887086c564bf22bc022",
            "ic_wechat" to "661dd03a2fe9da008a3a8471ff1580471be83307e6e9b1b422f65bc569d5d357",
            "ic_telegram" to "f5f42a5ace1166a701d254b16889065e80f8944d6b376850584fae7c6875f4d8",
            "ic_qq" to "8811486e917e22582eced4373033a42710262a9af2c4bd39a7a240b99db7ff76",
        )
        val all = expected + remix
        // 只保留既有清单的校验，不要求每个新增图标都加入完整性清单。
        all.forEach { (name, expectedDigest) ->
            val icon = File("src/main/res/drawable/$name.xml")
            val digest = MessageDigest.getInstance("SHA-256").digest(icon.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            assertEquals("图标与官方资源不一致：${icon.name}", expectedDigest, digest)
        }
    }
}
