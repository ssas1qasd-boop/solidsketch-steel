// v70 C03 · Apex haunch as Tekla 106 (docs/steel/PARITY.md "C03"): R12's apex, then other sizes.
'use strict';
module.exports = [
 {
  "name": "t106_r12_r3008",
  "description": "R12 apex at R3008 (BI220-8-150-10 rafters): end plates 20 x 400 (top 70 above the rafter), 4 rows M20, Tekla haunch 2000 horizontal / 83.373 plumb, flange 10 x 150 ends plumb, end plate stiffeners FLT10*60 (chamfer 30, 88 at the far end).",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "name": "RAFTER",
     "prof": "BI220-8-150-10",
     "p1": [
      52682.387,
      27925.0,
      7433.94
     ],
     "p2": [
      46290.5,
      27925.0,
      8887.194
     ],
     "rot": "Top",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "RAFTER",
    "extra": {
     "name": "RAFTER",
     "prof": "BI220-8-150-10",
     "p1": [
      39848.613,
      27925.0,
      7422.572
     ],
     "p2": [
      46290.5,
      27925.0,
      8887.194
     ],
     "rot": "Top",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "apex",
    [
     0,
     1
    ],
    {
     "tp": 20.0,
     "ph": 400.0,
     "pw": 160.0,
     "rowsAt": "50 120 260 330",
     "gauge": 80.0,
     "bolt": 20,
     "grade": "8.8",
     "top": 69.999,
     "len": 2000.0,
     "depth": 83.373,
     "hw": 8.0,
     "hf": 10.0,
     "hb": 150.0,
     "hrule": "tekla",
     "ast": 1,
     "asl": 60.0,
     "ach": 30.0,
     "att": 10.0,
     "ash": 87.988
    }
   ]
  ]
 },
 {
  "name": "t106_ipe400_10deg_defaults",
  "description": "IPE400 rafters at 10 deg, plate 25 x 600, Tekla haunch 1500 horizontal / 200 plumb, stiffeners with their default height (to the rafter top).",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "prof": "IPE400",
     "p1": [
      0,
      0,
      6000
     ],
     "p2": [
      6000,
      0,
      7057.98
     ],
     "rot": "Top",
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "RAFTER",
    "extra": {
     "prof": "IPE400",
     "p1": [
      12000,
      0,
      6000
     ],
     "p2": [
      6000,
      0,
      7057.98
     ],
     "rot": "Top",
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "apex",
    [
     0,
     1
    ],
    {
     "tp": 25,
     "ph": 600,
     "pw": 200,
     "rowsAt": "60 140 450 530",
     "gauge": 100,
     "bolt": 24,
     "top": 80,
     "len": 1500,
     "depth": 200,
     "hw": 10,
     "hf": 12,
     "hb": 180,
     "hrule": "tekla",
     "ast": 1,
     "asl": 70,
     "ach": 25,
     "att": 12
    }
   ]
  ]
 },
 {
  "name": "t106_hea300_5deg_no_stiff",
  "description": "HEA300 rafters at 5 deg, Tekla haunch 1200 / 150, no stiffeners (plate top 40 above).",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "prof": "HEA300",
     "p1": [
      0,
      0,
      5000
     ],
     "p2": [
      5000,
      0,
      5437.44
     ],
     "rot": "Top",
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "RAFTER",
    "extra": {
     "prof": "HEA300",
     "p1": [
      10000,
      0,
      5000
     ],
     "p2": [
      5000,
      0,
      5437.44
     ],
     "rot": "Top",
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "apex",
    [
     0,
     1
    ],
    {
     "tp": 20,
     "ph": 500,
     "pw": 320,
     "rowsAt": "70 150 360 440",
     "gauge": 160,
     "bolt": 20,
     "top": 40,
     "len": 1200,
     "depth": 150,
     "hrule": "tekla"
    }
   ]
  ]
 },
 {
  "name": "t106_r12_r3008_catalog",
  "description": "The same R12 apex through Apex haunch (106) from the catalog (Tekla dialog: plate height set = the Tekla apex).",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "name": "RAFTER",
     "prof": "BI220-8-150-10",
     "p1": [
      52682.387,
      27925.0,
      7433.94
     ],
     "p2": [
      46290.5,
      27925.0,
      8887.194
     ],
     "rot": "Top",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "RAFTER",
    "extra": {
     "name": "RAFTER",
     "prof": "BI220-8-150-10",
     "p1": [
      39848.613,
      27925.0,
      7422.572
     ],
     "p2": [
      46290.5,
      27925.0,
      8887.194
     ],
     "rot": "Top",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "T106",
    [
     0,
     1
    ],
    {
     "tp": 20.0,
     "ph": 400.0,
     "pw": 160.0,
     "rowsAt": "50 120 260 330",
     "gauge": 80.0,
     "bolt": 20,
     "grade": "8.8",
     "top": 69.999,
     "len": 2000.0,
     "depth": 83.373,
     "hw": 8.0,
     "hf": 10.0,
     "hb": 150.0,
     "hrule": "tekla",
     "ast": 1,
     "asl": 60.0,
     "ach": 30.0,
     "att": 10.0,
     "ash": 87.988,
     "kind": "apex"
    }
   ]
  ]
 }
];
