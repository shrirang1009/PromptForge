import * as esbuild from "esbuild";
import fs from "fs/promises";
import path from "path";
// @ts-ignore
import resolveConfig from "tailwindcss/resolveConfig.js";
import { type Plugin } from "vite";

const devRuntimeCode = `import * as ReactJSXDevRuntime from "react/jsx-dev-runtime";

const _jsxDEV = ReactJSXDevRuntime.jsxDEV;
export const Fragment = ReactJSXDevRuntime.Fragment;

const _isBrowser = typeof window !== "undefined";

const SOURCE_KEY = Symbol.for("__jsxSource__");

const cleanFileName = (fileName) => {
  if (!fileName) return "";
  if (fileName.includes("dev_server")) {
    fileName = fileName.split("dev_server")[1].slice(1);
  }
  if (fileName.includes("sandbox-scheduler/sandbox")) {
    const sandboxPart = fileName.split("sandbox-scheduler/")[1];
    fileName = sandboxPart.split("/").slice(1).join("/");
  }
  return fileName.replace(/^\\/dev-server\\//, "");
};

const sourceElementMap = new Map();
if (_isBrowser) {
  // @ts-ignore
  window.sourceElementMap = sourceElementMap;
}

function getSourceKey(sourceInfo) {
  return \`\${cleanFileName(sourceInfo.fileName)}:\${sourceInfo.lineNumber}:\${sourceInfo.columnNumber}\`;
}

function unregisterElement(node, sourceInfo) {
  const key = getSourceKey(sourceInfo);
  const refs = sourceElementMap.get(key);
  if (refs) {
    for (const ref of refs) {
      if (ref.deref() === node) {
        refs.delete(ref);
        break;
      }
    }
    if (refs.size === 0) {
      sourceElementMap.delete(key);
    }
  }
}

function registerElement(node, sourceInfo) {
  const key = getSourceKey(sourceInfo);
  if (!sourceElementMap.has(key)) {
    sourceElementMap.set(key, new Set());
  }
  sourceElementMap.get(key).add(new WeakRef(node));
}

function getTypeName(type) {
  if (typeof type === "string") return type;
  if (typeof type === "function") return type.displayName || type.name || "Unknown";
  if (typeof type === "object" && type !== null) {
    return type.displayName || type.render?.displayName || type.render?.name || "Unknown";
  }
  return "Unknown";
}

export function jsxDEV(type, props, key, isStatic, source, self) {
  // During SSR, skip all tagging and pass through to React's jsxDEV
  if (!_isBrowser) {
    return _jsxDEV(type, props, key, isStatic, source, self);
  }

  // For custom components (like <Icon />, <Button />), tag their rendered output
  // This captures the JSX element name for library components that don't have source info
  if (source?.fileName && typeof type !== "string" && type !== Fragment) {
    const typeName = getTypeName(type);
    const jsxSourceInfo = {
      fileName: cleanFileName(source.fileName),
      lineNumber: source.lineNumber,
      columnNumber: source.columnNumber,
      displayName: typeName,
    };

    const originalRef = props?.ref;
    const enhancedProps = {
      ...props,
      ref: (node) => {
        if (node) {
          // Only tag if this element doesn't already have source info
          // (library components won't have it, user components will)
          if (!node[SOURCE_KEY]) {
            node[SOURCE_KEY] = jsxSourceInfo;
            registerElement(node, jsxSourceInfo);
          }
        }
        if (typeof originalRef === "function") {
          originalRef(node);
        } else if (originalRef && typeof originalRef === "object") {
          originalRef.current = node;
        }
      },
    };

    return _jsxDEV(type, enhancedProps, key, isStatic, source, self);
  }

  // For host elements (div, span, etc.), tag with component context
  if (source?.fileName && typeof type === "string") {
    const sourceInfo = {
      fileName: cleanFileName(source.fileName),
      lineNumber: source.lineNumber,
      columnNumber: source.columnNumber,
      displayName: type,
    };

    const originalRef = props?.ref;

    const enhancedProps = {
      ...props,
      ref: (node) => {
        if (node) {
          const existingSource = node[SOURCE_KEY];
          if (existingSource) {
            if (getSourceKey(existingSource) !== getSourceKey(sourceInfo)) {
              unregisterElement(node, existingSource);
              node[SOURCE_KEY] = sourceInfo;
              registerElement(node, sourceInfo);
            }
          } else {
            node[SOURCE_KEY] = sourceInfo;
            registerElement(node, sourceInfo);
          }
        }
        if (typeof originalRef === "function") {
          originalRef(node);
        } else if (originalRef && typeof originalRef === "object") {
          originalRef.current = node;
        }
      },
    };
    return _jsxDEV(type, enhancedProps, key, isStatic, source, self);
  }

  return _jsxDEV(type, props, key, isStatic, source, self);
}
`;

function createJsxTaggerFeature() {
  return {
    resolveId(id: string, importer?: string) {
      if (id === "react/jsx-dev-runtime" && !importer?.includes("\0jsx-source")) {
        return "\0jsx-source/jsx-dev-runtime";
      }
      return null;
    },
    load(id: string) {
      if (id === "\0jsx-source/jsx-dev-runtime") {
        return devRuntimeCode;
      }
      return null;
    }
  };
}

const V4_CSS_CANDIDATES = ["src/styles.css", "src/index.css", "src/globals.css", "src/app.css"];
const V4_NAMESPACES = {
  colors: "--color",
  screens: "--breakpoint",
  spacing: "--spacing",
  borderRadius: "--radius",
  fontFamily: "--font",
  opacity: "--opacity"
};

async function findV4CssEntry(projectRoot: string) {
  for (const candidate of V4_CSS_CANDIDATES) {
    const abs = path.resolve(projectRoot, candidate);
    try {
      const contents = await fs.readFile(abs, "utf8");
      if (/@import\s+["']tailwindcss/.test(contents)) {
        return abs;
      }
    } catch {
    }
  }
  return null;
}

async function loadDesignSystemFromProject() {
  try {
    const modName = "@tailwindcss/node";
    const mod = await import(modName);
    return mod.__unstable__loadDesignSystem;
  } catch {
    return null;
  }
}

function parseRootVars(css: string) {
  const vars: Record<string, string> = {};
  const rootMatch = css.match(/:root\s*\{([^}]*)\}/s);
  if (!rootMatch)
    return vars;
  for (const decl of rootMatch[1].split(";")) {
    const trimmed = decl.trim();
    if (!trimmed.startsWith("--"))
      continue;
    const colon = trimmed.indexOf(":");
    if (colon === -1)
      continue;
    const key = trimmed.slice(0, colon).trim();
    const value = trimmed.slice(colon + 1).trim();
    if (key && value)
      vars[key] = value;
  }
  return vars;
}

function resolveVars(value: string, rootVars: Record<string, string>, depth = 0): string {
  if (depth > 8)
    return value;
  return value.replace(/var\(\s*(--[\w-]+)(?:\s*,\s*([^)]+))?\s*\)/g, (match, name, fallback) => {
    const resolved = rootVars[name];
    if (resolved !== void 0) {
      return resolveVars(resolved, rootVars, depth + 1);
    }
    return fallback ?? match;
  });
}

async function generateV4Config(cssEntry: string, outfile: string, load: any) {
  const css = await fs.readFile(cssEntry, "utf8");
  const ds = await load(css, { base: path.dirname(cssEntry) });
  const rootVars = parseRootVars(css);
  const theme: Record<string, any> = {};
  for (const [configKey, namespace] of Object.entries(V4_NAMESPACES)) {
    const entries: Record<string, string> = {};
    for (const [key, rawValue] of ds.theme.namespace(namespace)) {
      if (key === null)
        continue;
      entries[key] = resolveVars(rawValue, rootVars);
    }
    theme[configKey] = entries;
  }
  await fs.mkdir(path.dirname(outfile), { recursive: true });
  await fs.writeFile(outfile, JSON.stringify({ theme }, null, 2));
}

function createTailwindConfigFeature() {
  let projectRoot = "";
  const generateV3Config = async (tailwindInputFile: string, tailwindIntermediateFile: string, tailwindJsonOutfile: string) => {
    try {
      await esbuild.build({
        entryPoints: [tailwindInputFile],
        outfile: tailwindIntermediateFile,
        bundle: true,
        format: "esm",
        banner: {
          js: 'import { createRequire } from "module"; const require = createRequire(import.meta.url);'
        }
      });
      try {
        const userConfig = await import(tailwindIntermediateFile + "?update=" + Date.now());
        if (!userConfig || !userConfig.default) {
          console.error("Invalid Tailwind config structure:", userConfig);
          throw new Error("Invalid Tailwind config structure");
        }
        const resolvedConfig = resolveConfig(userConfig.default);
        await fs.writeFile(tailwindJsonOutfile, JSON.stringify(resolvedConfig, null, 2));
        await fs.unlink(tailwindIntermediateFile).catch(() => {
        });
      } catch (error) {
        console.error("Error processing config:", error);
        throw error;
      }
    } catch (error) {
      console.error("Error in generateConfig:", error);
      throw error;
    }
  };
  const run = async () => {
    const outfile = path.resolve(projectRoot, "./src/tailwind.config.pf.json");
    const cssEntry = await findV4CssEntry(projectRoot);
    if (cssEntry) {
      const load = await loadDesignSystemFromProject();
      if (load) {
        try {
          await generateV4Config(cssEntry, outfile, load);
          console.log(`[promptforge-tagger] wrote ${outfile}`);
          return;
        } catch (error) {
          console.error("Error generating tailwind.config.pf.json:", error);
          return;
        }
      }
    }
    const v3ConfigFile = path.resolve(projectRoot, "./tailwind.config.ts");
    try {
      await fs.access(v3ConfigFile);
    } catch {
      return;
    }
    const intermediate = path.resolve(projectRoot, "./.pf.tailwind.config.js");
    try {
      await generateV3Config(v3ConfigFile, intermediate, outfile);
      console.log(`[promptforge-tagger] wrote ${outfile}`);
    } catch (error) {
      console.error("Error generating tailwind.config.pf.json:", error);
    }
  };
  return {
    onConfigResolved(config: any) {
      projectRoot = config.root;
    },
    async onBuildStart() {
      await run();
    },
    onConfigureServer(server: any) {
      try {
        const v3ConfigFile = path.resolve(projectRoot, "./tailwind.config.ts");
        const v4Candidates = V4_CSS_CANDIDATES.map((c) => path.resolve(projectRoot, c));
        const watchPaths = [v3ConfigFile, ...v4Candidates];
        for (const p of watchPaths) {
          server.watcher.add(p);
        }
        const normalized = new Set(watchPaths.map((p) => path.normalize(p)));
        server.watcher.on("change", async (changedPath: string) => {
          if (normalized.has(path.normalize(changedPath))) {
            await run();
          }
        });
      } catch (error) {
        console.error("Error adding watcher:", error);
      }
    }
  };
}

const isSandbox = process.env.PROMPTFORGE_DEV_SERVER === "true";

export interface PromptForgeTaggerOptions {
  jsxSource?: boolean;
  tailwindConfig?: boolean;
}

export function promptforgeTagger({
  jsxSource = isSandbox,
  tailwindConfig = isSandbox
}: PromptForgeTaggerOptions = {}): Plugin {
  const features: any[] = [];
  if (jsxSource) {
    features.push(createJsxTaggerFeature());
  }
  if (tailwindConfig) {
    features.push(createTailwindConfigFeature());
  }
  return {
    name: "promptforge-plugin",
    enforce: "pre",
    configResolved(config) {
      for (const feature of features) {
        feature.onConfigResolved?.(config);
      }
    },
    async buildStart() {
      for (const feature of features) {
        await feature.onBuildStart?.();
      }
    },
    configureServer(server) {
      for (const feature of features) {
        feature.onConfigureServer?.(server);
      }
    },
    resolveId(id, importer) {
      for (const feature of features) {
        const result = feature.resolveId?.(id, importer);
        if (result !== null && result !== void 0) {
          return result;
        }
      }
      return null;
    },
    load(id) {
      for (const feature of features) {
        const result = feature.load?.(id);
        if (result !== null && result !== void 0) {
          return result;
        }
      }
      return null;
    }
  };
}

export { promptforgeTagger as componentTagger };
