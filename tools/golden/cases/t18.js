// v70 C18 · Stairs as Tekla (docs/steel/PARITY.md "C18"): R12's two flights (treads, brackets, mitre fits, stair end plates), an odd and a skewed stair.
'use strict';
module.exports = [
 {
  "name": "t18_r12_flight1",
  "description": "R12 lower flight (SR9001 / SR9002): 12 grating treads PL10*335.7 on L60*6 brackets (2 M12 each), the sloped stringers mitred to their foot and landing pieces; stair end plates: 2 feet (2 x 2 M16 L12 outward), 2 landing plates bolted through posts STR9001 / STR9002 (2 x 4 M16).",
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
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      1650.5,
      8289.137,
      78.835
     ],
     "p2": [
      1650.5,
      12035.972,
      1972.906
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      565.5,
      8355,
      185.929
     ],
     "p2": [
      565.5,
      8355,
      12
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
   },
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      565.5,
      12142,
      1960
     ],
     "p2": [
      565.5,
      11981.834,
      1960
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      1650.5,
      8355,
      12
     ],
     "p2": [
      1650.5,
      8355,
      185.929
     ],
     "rot": "Top",
     "rAng": 270,
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      1650.5,
      11981.834,
      1960
     ],
     "p2": [
      1650.5,
      12142,
      1960
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
     "name": "COLUMN",
     "prof": "BI162-6-150-6",
     "p1": [
      598.0,
      12235.01,
      30.0
     ],
     "p2": [
      598.0,
      12235.01,
      2080.0
     ],
     "rot": "Back",
     "rAng": 0,
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
     "name": "COLUMN",
     "prof": "BI162-6-150-6",
     "p1": [
      1619.0,
      12235.01,
      30.0
     ],
     "p2": [
      1619.0,
      12235.01,
      2080.0
     ],
     "rot": "Back",
     "rAng": 0,
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
    "stairtreads",
    [
     0,
     1,
     2,
     3,
     4,
     5
    ],
    {
     "tmode": "tekla",
     "ntr": 12,
     "rise": 156.667,
     "s0": 145.863,
     "dz": 47.429
    }
   ],
   [
    "Tsep",
    [
     2
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 260.0,
     "de": 0.0,
     "dw": -32.5,
     "end": "2",
     "bd": 16,
     "nr": 2,
     "nc": 2,
     "bp": 120.0,
     "bg": 70.0,
     "by": 0.0,
     "bx": -32.5,
     "bside": "out",
     "bgrip": 0,
     "bL": 12
    }
   ],
   [
    "Tsep",
    [
     3,
     6
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 320.0,
     "de": 40.0,
     "dw": -32.5,
     "end": "1",
     "bd": 16,
     "nr": 4,
     "nc": 2,
     "bp": 85.0,
     "bg": 70.0,
     "by": 38.7,
     "bx": -32.9,
     "bside": "main",
     "mgrip": 6.0
    }
   ],
   [
    "Tsep",
    [
     5,
     7
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 320.0,
     "de": 40.0,
     "dw": -32.5,
     "end": "2",
     "bd": 16,
     "nr": 4,
     "nc": 2,
     "bp": 85.0,
     "bg": 70.0,
     "by": 38.7,
     "bx": -32.9,
     "bside": "main",
     "mgrip": 6.0
    }
   ],
   [
    "Tsep",
    [
     4
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 260.0,
     "de": 0.0,
     "dw": -32.5,
     "end": "1",
     "bd": 16,
     "nr": 2,
     "nc": 2,
     "bp": 120.0,
     "bg": 70.0,
     "by": 0.0,
     "bx": -32.5,
     "bside": "out",
     "bgrip": 0,
     "bL": 12
    }
   ]
  ]
 },
 {
  "name": "t18_r12_flight2",
  "description": "R12 / R13 upper flight (SR9003 / SR9004): 11 treads at rise 170, mitre fits, landing plates through posts STR9003 / STR9004, top plates (PLSTR9001, loose: no weld, their own assembly) bolted outward through the counter plates FLT12*150 on girder EB4002 (grip 24).",
  "members": [
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      565.5,
      16600.795,
      3896.424
     ],
     "p2": [
      565.5,
      13262.938,
      1943.576
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      1650.5,
      13262.938,
      1943.576
     ],
     "p2": [
      1650.5,
      16600.795,
      3896.424
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      565.5,
      13323.536,
      1960
     ],
     "p2": [
      565.5,
      13175.75,
      1960
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      565.5,
      16701,
      3880
     ],
     "p2": [
      565.5,
      16540.197,
      3880
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      1650.5,
      13175.75,
      1960
     ],
     "p2": [
      1650.5,
      13323.536,
      1960
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
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      1650.5,
      16540.197,
      3880
     ],
     "p2": [
      1650.5,
      16701,
      3880
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
     "name": "COLUMN",
     "prof": "BI162-6-150-6",
     "p1": [
      598.0,
      13082.75,
      30.0
     ],
     "p2": [
      598.0,
      13082.75,
      2080.0
     ],
     "rot": "Back",
     "rAng": 0,
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
     "name": "COLUMN",
     "prof": "BI162-6-150-6",
     "p1": [
      1619.0,
      13082.75,
      30.0
     ],
     "p2": [
      1619.0,
      13082.75,
      2080.0
     ],
     "rot": "Back",
     "rAng": 0,
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
     "name": "BEAM",
     "prof": "BI320-8-120-10",
     "p1": [
      334.0,
      16785.0,
      3840.0
     ],
     "p2": [
      13127.0,
      16785.0,
      3840.0
     ],
     "rot": "Top",
     "rAng": 0,
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
    "stairtreads",
    [
     0,
     1,
     2,
     3,
     4,
     5
    ],
    {
     "tmode": "tekla",
     "ntr": 11,
     "rise": 170.0,
     "s0": 250.812,
     "dz": 39.684
    }
   ],
   [
    "Tsep",
    [
     2,
     6
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 320.0,
     "de": 40.0,
     "dw": -32.5,
     "end": "2",
     "bd": 16,
     "nr": 4,
     "nc": 2,
     "bp": 85.0,
     "bg": 70.0,
     "by": 38.7,
     "bx": -32.9,
     "bside": "main",
     "mgrip": 6.0
    }
   ],
   [
    "Tsep",
    [
     4,
     7
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 320.0,
     "de": 40.0,
     "dw": -32.5,
     "end": "1",
     "bd": 16,
     "nr": 4,
     "nc": 2,
     "bp": 85.0,
     "bg": 70.0,
     "by": 38.7,
     "bx": -32.9,
     "bside": "main",
     "mgrip": 6.0
    }
   ],
   [
    "Tsep",
    [
     5,
     8
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 320.0,
     "de": 40.0,
     "dw": -32.5,
     "end": "2",
     "bd": 16,
     "nr": 4,
     "nc": 2,
     "bp": 85.0,
     "bg": 70.0,
     "by": 38.7,
     "bx": -32.9,
     "bside": "out",
     "bgrip": 0,
     "cplate": 1,
     "pweld": 0
    }
   ],
   [
    "Tsep",
    [
     3,
     8
    ],
    {
     "t": 12.0,
     "b": 150.0,
     "h": 320.0,
     "de": 40.0,
     "dw": -32.5,
     "end": "1",
     "bd": 16,
     "nr": 4,
     "nc": 2,
     "bp": 85.0,
     "bg": 70.0,
     "by": 38.7,
     "bx": -32.9,
     "bside": "out",
     "bgrip": 0,
     "cplate": 1,
     "pweld": 0
    }
   ]
  ]
 },
 {
  "name": "t18_upn200_defaults",
  "description": "UPN200 stringers 900 apart, rise 180, 9 treads with the Tekla mode defaults (tread 300 x 40, gap 5, brackets L60*6 x 140), no fit pieces; a foot end plate outward.",
  "members": [
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN200",
     "p1": [
      0,
      0,
      0
     ],
     "p2": [
      0,
      3200,
      1800
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN200",
     "p1": [
      900,
      0,
      0
     ],
     "p2": [
      900,
      3200,
      1800
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "stairtreads",
    [
     0,
     1
    ],
    {
     "tmode": "tekla",
     "ntr": 9,
     "rise": 180,
     "s0": 200,
     "dz": 40
    }
   ],
   [
    "Tsep",
    [
     0
    ],
    {
     "end": "1",
     "h": 200,
     "b": 120,
     "t": 10,
     "nr": 2,
     "nc": 1,
     "bp": 100,
     "bside": "out",
     "bL": 20
    }
   ]
  ]
 },
 {
  "name": "t18_skew40_upn240",
  "description": "UPN240 stringers running at 40 deg in plan, 1000 apart, rise 160, 10 treads, tread 280 x 40 PL8, bracket bolts M12 at 80.",
  "members": [
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      0,
      0,
      0
     ],
     "p2": [
      2298.1,
      1928.4,
      1700
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle"
    }
   },
   {
    "set": "BEAM",
    "extra": {
     "name": "STRINGER",
     "prof": "UPN240",
     "p1": [
      642.8,
      -766.0,
      0
     ],
     "p2": [
      2940.9,
      1162.4,
      1700
     ],
     "rot": "Top",
     "rAng": 180,
     "plane": "Middle",
     "depth": "Middle"
    }
   }
  ],
  "conns": [
   [
    "stairtreads",
    [
     0,
     1
    ],
    {
     "tmode": "tekla",
     "ntr": 10,
     "rise": 160,
     "s0": 220,
     "dz": 45,
     "tw": 280,
     "tt": 8,
     "bp": 80
    }
   ]
  ]
 }
];
