// v70 C19 · Railing welds as Tekla (docs/steel/PARITY.md "C19"): one R13 stair railing, members as given, each stanchion welded to the stringer it stands on.
'use strict';
module.exports = [
 {
  "name": "t19_r13_flight1_railing",
  "description": "R13 lower flight, left railing (SR9002 / HR9002): the sloped stringer UPN240 (844), its 6 stanchions CHS48*3 (833, 832, 809, 808, 807, 806), the hand rail CHS48*3 (804) and the railing PD50*5 (805) as given; Weld (no parts) a = 2, fit = face, from the stringer to each stanchion, as Tekla: the foot cut on the stringer's sloped top flange, the top square at the hand rail's centre line (C19).",
  "r13": [
   844,
   833,
   832,
   809,
   808,
   807,
   806,
   804,
   805
  ],
  "members": [
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      565.5,
      12035.972,
      1972.906
     ],
     "p2": [
      565.5,
      8289.137,
      78.835
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "Stanchion",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      8335,
      224.348
     ],
     "p2": [
      565.5,
      8335,
      1236.481
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "Stanchion",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      9035,
      578.207
     ],
     "p2": [
      565.5,
      9035,
      1590.339
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "Stanchion",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      9735,
      932.066
     ],
     "p2": [
      565.5,
      9735,
      1944.198
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "Stanchion",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      10435,
      1285.924
     ],
     "p2": [
      565.5,
      10435,
      2298.056
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "Stanchion",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      11135,
      1639.783
     ],
     "p2": [
      565.5,
      11135,
      2651.915
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "COLUMN",
    "extra": {
     "name": "Stanchion",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      11810.441,
      1981.226
     ],
     "p2": [
      565.5,
      11810.441,
      2993.359
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "BEAM",
    "extra": {
     "name": "Hand Rail",
     "prof": "CHS48*3",
     "p1": [
      565.5,
      8335,
      1236.481
     ],
     "p2": [
      565.5,
      11810.441,
      2993.359
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   },
   {
    "set": "BEAM",
    "extra": {
     "name": "RAILING",
     "prof": "PD50*5",
     "p1": [
      565.5,
      8335,
      736.481
     ],
     "p2": [
      565.5,
      11810.441,
      2493.359
     ],
     "rot": "Top",
     "rAng": 90,
     "plane": "Middle",
     "depth": "Middle",
     "pOff": 0,
     "dOff": 0,
     "dx1": 0,
     "dx2": 0
    }
   }
  ],
  "conns": [
   [
    "weldonly",
    [
     0,
     1
    ],
    {
     "a": 2,
     "fit": "face"
    }
   ],
   [
    "weldonly",
    [
     0,
     2
    ],
    {
     "a": 2,
     "fit": "face"
    }
   ],
   [
    "weldonly",
    [
     0,
     3
    ],
    {
     "a": 2,
     "fit": "face"
    }
   ],
   [
    "weldonly",
    [
     0,
     4
    ],
    {
     "a": 2,
     "fit": "face"
    }
   ],
   [
    "weldonly",
    [
     0,
     5
    ],
    {
     "a": 2,
     "fit": "face"
    }
   ],
   [
    "weldonly",
    [
     0,
     6
    ],
    {
     "a": 2,
     "fit": "face"
    }
   ]
  ]
 }
];
