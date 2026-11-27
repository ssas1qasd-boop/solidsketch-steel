// v70 C17 · Shear plate 146 as a hanger under a sloped rafter (docs/steel/PARITY.md "C17"): R12's gable column tops, then a steeper case.
'use strict';
module.exports = [
 {
  "name": "t146h_r12_r3002_egc2001",
  "description": "R12 gable hanger, tab on the uphill side of the column web (R3002 / EGC2001): FLT10*120 x 190 from the rafter face at the web's uphill face, 4 M16 L40, posV 35.",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "name": "RAFTER",
     "prof": "BI220-8-150-10",
     "p1": [
      39798.613,
      75.0,
      7411.204
     ],
     "p2": [
      46290.5,
      75.0,
      8887.194
     ],
     "rot": "Top",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "COLUMN",
     "prof": "BI220-6-200-10",
     "p1": [
      43290.5,
      110.0,
      230.0
     ],
     "p2": [
      43290.5,
      110.0,
      8043.935
     ],
     "rot": "Back",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "T146",
    [
     0,
     1
    ],
    {
     "t": 10.0,
     "bolt": 16,
     "rows": 2,
     "e": 30.0,
     "pitch": 60.0,
     "cols": 2,
     "pitchH": 60.0,
     "gap": 49.056,
     "side": 1,
     "hang": "face",
     "refV": "Middle",
     "posV": 34.994,
     "tb": 190.0,
     "eh": 40.948,
     "blrule": "tekla"
    }
   ]
  ]
 },
 {
  "name": "t146h_r12_r3008_downhill",
  "description": "R12 gable hanger, tab on the downhill side (R3008 / EGC2001): the same 190 / 49.056 from the uphill web face, the tab top cut on the sloped underside.",
  "members": [
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
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "COLUMN",
     "prof": "BI220-6-200-10",
     "p1": [
      43290.5,
      27890.0,
      230.0
     ],
     "p2": [
      43290.5,
      27890.0,
      8043.935
     ],
     "rot": "Front",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "T146",
    [
     0,
     1
    ],
    {
     "t": 10.0,
     "bolt": 16,
     "rows": 2,
     "e": 30.0,
     "pitch": 60.0,
     "cols": 2,
     "pitchH": 60.0,
     "gap": 49.056,
     "side": 1,
     "hang": "face",
     "refV": "Middle",
     "posV": 34.994,
     "tb": 190.0,
     "eh": 40.949,
     "blrule": "tekla"
    }
   ]
  ]
 },
 {
  "name": "t146h_r12_eg3502",
  "description": "R12 EG3502: the hanger that is its own assembly in Tekla, under R3024 on EGC2002.",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "name": "RAFTER",
     "prof": "BI220-8-150-10",
     "p1": [
      313.613,
      27925.0,
      7433.94
     ],
     "p2": [
      6705.5,
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
    "set": "COLUMN",
    "extra": {
     "name": "COLUMN",
     "prof": "BI220-6-200-10",
     "p1": [
      3705.5,
      27890.0,
      230.0
     ],
     "p2": [
      3705.5,
      27890.0,
      8043.935
     ],
     "rot": "Front",
     "rAng": 0,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "T146",
    [
     0,
     1
    ],
    {
     "t": 10.0,
     "bolt": 16,
     "rows": 2,
     "e": 30.0,
     "pitch": 60.0,
     "cols": 2,
     "pitchH": 60.0,
     "gap": 49.062,
     "side": 1,
     "hang": "face",
     "refV": "Middle",
     "posV": 34.994,
     "tb": 190.005,
     "eh": 40.949,
     "blrule": "tekla"
    }
   ]
  ]
 },
 {
  "name": "t146h_ipe300_hea200_15deg",
  "description": "IPE300 rafter at 15 deg over a HEA200 column top: hanger 220 long, 2 x 3 M16, from the uphill web face.",
  "members": [
   {
    "set": "RAFTER",
    "extra": {
     "prof": "IPE300",
     "p1": [
      0,
      0,
      5000
     ],
     "p2": [
      6000,
      0,
      6607.7
     ],
     "rot": "Top",
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "prof": "HEA200",
     "p1": [
      3000,
      0,
      0
     ],
     "p2": [
      3000,
      0,
      5600
     ],
     "rot": "Top",
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "T146",
    [
     0,
     1
    ],
    {
     "t": 10,
     "bolt": 16,
     "rows": 2,
     "e": 30,
     "pitch": 60,
     "cols": 3,
     "pitchH": 55,
     "gap": 40,
     "side": 1,
     "hang": "face",
     "refV": "Middle",
     "posV": 0,
     "tb": 220,
     "eh": 35,
     "blrule": "tekla"
    }
   ]
  ]
 }
];
