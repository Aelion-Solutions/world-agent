import { z } from "zod";

export const regionShape = {
  world: z.string(),
  x1: z.number().int().optional(),
  y1: z.number().int().optional(),
  z1: z.number().int().optional(),
  x2: z.number().int().optional(),
  y2: z.number().int().optional(),
  z2: z.number().int().optional(),
  x: z.number().optional(),
  y: z.number().optional(),
  z: z.number().optional(),
  radius: z.number().optional(),
  radius_y: z.number().optional(),
};

const regionKeys = [
  "x1",
  "y1",
  "z1",
  "x2",
  "y2",
  "z2",
  "x",
  "y",
  "z",
  "radius",
  "radius_y",
] as const;

export type RegionArgs = {
  world: string;
  x1?: number;
  y1?: number;
  z1?: number;
  x2?: number;
  y2?: number;
  z2?: number;
  x?: number;
  y?: number;
  z?: number;
  radius?: number;
  radius_y?: number;
};

export function regionQuery(args: RegionArgs): Record<string, string | number | undefined> {
  const q: Record<string, string | number | undefined> = { world: args.world };
  for (const key of regionKeys) {
    if (args[key] !== undefined) {
      q[key] = args[key] as number;
    }
  }
  return q;
}

export function textResult(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }],
  };
}
