# C09 End plate (144) Haunch tab: video evidence (reference order 3)

Video: https://www.youtube.com/watch?v=rf0oBDvnNjE ("How to create End plate 144 connection between column to beam in Tekla", imperial units).

- Gemini (`gemini_rf0oBDvnNjE.json`, model gemini-flash-latest): the 144 dialog's **Haunch** tab at 10:53, 11:08, 11:35 and 12:15.
  - Fields: Top plate t / b / h, Bottom plate t / b / h, chamfer fields; all blank at 10:53 (Tekla's default: no haunch plates).
  - 11:08: t = 3/16" on both plates, b and h blank: "top and bottom haunch plates" are created on the beam flanges at the extended end plate; their length follows the end plate's reach past the flange.
  - 11:35 / 12:15: b = 5", then 4": the plates get shorter.
- Frames: the Apify actor `emttn/frameskim` answered "YouTube blocked this request; retry later. Nothing was charged." (2026-11-26), so Gemini's reading could not be cross-checked with frames yet. Its values are used only for the field list and the blank defaults; every size comes from R12 (24 / 24 joints the same: t 8, b 60, h 60, chamfers 15 x 15).
- The two eave videos (2FK1f5_Nzj4 "Beam to Column Eave Connection", MjAJSPlILsY "column - rafter haunch") could not be read: Gemini closed the connection / returned 503 three times.
