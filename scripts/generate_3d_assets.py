"""Generate the small PBR GLB assets used by Stack's real-time renderer."""

import io
import json
import math
import random
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "app" / "src" / "main" / "assets" / "models"
SOURCE = ROOT / "scripts" / "source_assets"


def png_bytes(image):
    stream = io.BytesIO()
    # Models occupy a small part of the display; 2K source maps waste texture bandwidth.
    image.thumbnail((512, 512), Image.Resampling.LANCZOS)
    image.convert("RGB").save(stream, format="JPEG", quality=88, optimize=True, progressive=True)
    return stream.getvalue()


def normal_texture(texture_bytes, strength=2.4):
    height = np.asarray(Image.open(io.BytesIO(texture_bytes)).convert("L"), dtype=np.float32) / 255.0
    broad = np.asarray(
        Image.fromarray(np.uint8(height * 255)).filter(ImageFilter.GaussianBlur(5)),
        dtype=np.float32,
    ) / 255.0
    height = height + (height - broad) * 1.8
    dy, dx = np.gradient(height)
    nx = -dx * strength
    ny = np.ones_like(height)
    nz = -dy * strength
    length = np.sqrt(nx * nx + ny * ny + nz * nz)
    normal = np.stack(((nx / length) * .5 + .5, (nz / length) * .5 + .5, (ny / length) * .5 + .5), axis=-1)
    return png_bytes(Image.fromarray(np.uint8(np.clip(normal * 255, 0, 255))))


def orm_texture(texture_bytes, roughness, metallic=0.0):
    """Pack glTF occlusion, roughness, and metallic channels into one texture."""
    height = np.asarray(Image.open(io.BytesIO(texture_bytes)).convert("L"), dtype=np.float32) / 255.0
    broad = np.asarray(
        Image.fromarray(np.uint8(height * 255)).filter(ImageFilter.GaussianBlur(9)),
        dtype=np.float32,
    ) / 255.0
    micro = np.clip(height - broad, -0.22, 0.22)
    occlusion = np.clip(0.94 + micro * 0.95, 0.68, 1.0)
    rough = np.clip(roughness - micro * 0.55, 0.38, 1.0)
    metal = np.full_like(height, metallic)
    packed = np.stack((occlusion, rough, metal), axis=-1)
    return png_bytes(Image.fromarray(np.uint8(np.clip(packed * 255, 0, 255))))


def packed_orm(roughness_path, ao_path=None):
    roughness = np.asarray(Image.open(roughness_path).convert("L"), dtype=np.uint8)
    if ao_path:
        ao = np.asarray(Image.open(ao_path).convert("L").resize((roughness.shape[1], roughness.shape[0])), dtype=np.uint8)
    else:
        ao = np.full_like(roughness, 255)
    metallic = np.zeros_like(roughness)
    return png_bytes(Image.fromarray(np.stack((ao, roughness, metallic), axis=-1)))


def studio_hdr(width=1024, height=512):
    """Create a compact high-dynamic-range studio environment for PBR lighting."""
    y, x = np.mgrid[0:height, 0:width]
    u = x / width
    v = y / height
    base = np.zeros((height, width, 3), dtype=np.float32)
    base[:] = [0.055, 0.06, 0.055]
    base += ((1.0 - v)[:, :, None] ** 3) * np.array([0.13, 0.15, 0.14], dtype=np.float32)

    def softbox(center_u, center_v, radius_u, radius_v, color, power):
        du = np.minimum(np.abs(u - center_u), 1.0 - np.abs(u - center_u)) / radius_u
        dv = np.abs(v - center_v) / radius_v
        falloff = np.exp(-(du * du + dv * dv) * 3.2)[:, :, None]
        return falloff * np.asarray(color, dtype=np.float32) * power

    base += softbox(.18, .31, .12, .16, [1.0, .88, .72], 8.0)
    base += softbox(.73, .38, .17, .20, [.66, .82, 1.0], 4.5)
    base += softbox(.48, .12, .35, .09, [1.0, .96, .87], 2.4)

    payload = bytearray(b"#?RADIANCE\nFORMAT=32-bit_rle_rgbe\n\n-Y %d +X %d\n" % (height, width))
    for rgb in base.reshape(-1, 3):
        maximum = float(rgb.max())
        if maximum < 1e-32:
            payload.extend((0, 0, 0, 0))
            continue
        mantissa, exponent = math.frexp(maximum)
        scale = mantissa * 256.0 / maximum
        payload.extend(
            (
                min(255, int(rgb[0] * scale)),
                min(255, int(rgb[1] * scale)),
                min(255, int(rgb[2] * scale)),
                exponent + 128,
            )
        )
    return bytes(payload)


def paper_texture(seed=7, size=1024):
    rng = np.random.default_rng(seed)
    base = np.full((size, size, 3), [205, 198, 183], dtype=np.float32)
    noise = rng.normal(0, 8, (size, size, 1))
    blurred = np.asarray(Image.fromarray(np.uint8(np.clip(noise + 128, 0, 255))[:, :, 0]).filter(ImageFilter.GaussianBlur(3)), dtype=np.float32)[:, :, None] - 128
    base += noise * 0.22 + blurred * 0.65
    image = Image.fromarray(np.uint8(np.clip(base, 0, 255)))
    draw = ImageDraw.Draw(image, "RGBA")
    for _ in range(650):
        x = rng.integers(0, size)
        y = rng.integers(0, size)
        length = rng.integers(2, 18)
        angle = rng.uniform(0, math.tau)
        draw.line((x, y, x + math.cos(angle) * length, y + math.sin(angle) * length), fill=(235, 231, 218, rng.integers(18, 65)), width=1)
    return png_bytes(image)


def white_paper_texture(texture_bytes):
    source = np.asarray(Image.open(io.BytesIO(texture_bytes)).convert("RGB"), dtype=np.float32)
    luminance = source.mean(axis=2, keepdims=True)
    broad = np.asarray(
        Image.fromarray(np.uint8(np.clip(luminance[:, :, 0], 0, 255))).filter(ImageFilter.GaussianBlur(9)),
        dtype=np.float32,
    )[:, :, None]
    fiber_detail = (luminance - broad) * 0.42
    tonal_detail = (broad - broad.mean()) * 0.12
    white = np.full_like(source, [244.0, 244.0, 242.0]) + fiber_detail + tonal_detail
    return png_bytes(Image.fromarray(np.uint8(np.clip(white, 222, 250))))


def stone_texture(seed=11, size=1024):
    rng = np.random.default_rng(seed)
    field = rng.normal(0, 1, (size, size))
    coarse = np.asarray(Image.fromarray(np.uint8((field - field.min()) / (field.max() - field.min()) * 255)).filter(ImageFilter.GaussianBlur(14)), dtype=np.float32) / 255
    fine = np.asarray(
        Image.fromarray(np.uint8(rng.random((size, size)) * 255)).filter(ImageFilter.GaussianBlur(1.3)),
        dtype=np.float32,
    ) / 255
    tone = 76 + coarse * 44 + (fine - .5) * 10
    arr = np.stack((tone * 1.02, tone, tone * 0.94), axis=-1)
    image = Image.fromarray(np.uint8(np.clip(arr, 0, 255)))
    draw = ImageDraw.Draw(image, "RGBA")
    points = []
    y = int(size * .58)
    for x in range(-20, size + 20, 8):
        y += rng.integers(-9, 10)
        points.append((x, y))
    draw.line(points, fill=(205, 198, 176, 150), width=4)
    draw.line([(x, y + 3) for x, y in points], fill=(28, 30, 28, 80), width=2)
    return png_bytes(image)


def book_texture(seed=19, size=1024):
    rng = np.random.default_rng(seed)
    arr = np.full((size, size, 3), [53, 61, 53], dtype=np.float32)
    arr += rng.normal(0, 5, (size, size, 1))
    image = Image.fromarray(np.uint8(np.clip(arr, 0, 255)))
    draw = ImageDraw.Draw(image, "RGBA")
    for x in range(0, size, 5):
        draw.line((x, 0, x + rng.integers(-5, 6), size), fill=(205, 188, 145, 15), width=1)
    draw.rectangle((28, 28, size - 28, size - 28), outline=(181, 147, 82, 105), width=3)
    return png_bytes(image)


def paper_mesh(seed=1, width=2.5, depth=1.75, thickness=.055, segments=24):
    rng = random.Random(seed)
    boundary = []
    edges = [((-width/2, -depth/2), (width/2, -depth/2)), ((width/2, -depth/2), (width/2, depth/2)), ((width/2, depth/2), (-width/2, depth/2)), ((-width/2, depth/2), (-width/2, -depth/2))]
    for edge_index, (start, end) in enumerate(edges):
        for i in range(segments):
            t = i / segments
            x = start[0] + (end[0] - start[0]) * t
            z = start[1] + (end[1] - start[1]) * t
            jitter = rng.uniform(-.035, .035)
            if edge_index in (0, 2): z += jitter
            else: x += jitter
            boundary.append((x, z))
    positions, normals, uvs, indices = [], [], [], []
    n = len(boundary)
    for y, normal, reverse in ((thickness/2, (0, 1, 0), True), (-thickness/2, (0, -1, 0), False)):
        center = len(positions); positions.append((0, y, 0)); normals.append(normal); uvs.append((.5, .5))
        ring = len(positions)
        for x, z in boundary:
            positions.append((x, y + rng.uniform(-.006, .006), z)); normals.append(normal); uvs.append((x/width + .5, z/depth + .5))
        for i in range(n):
            tri = (center, ring+i, ring+(i+1)%n)
            indices.extend(reversed(tri) if reverse else tri)
    side = len(positions)
    for i, (x, z) in enumerate(boundary):
        j = (i + 1) % n
        x2, z2 = boundary[j]
        nx, nz = z2-z, -(x2-x)
        length = max(math.hypot(nx, nz), 1e-5); normal=(nx/length, 0, nz/length)
        for p in ((x, thickness/2, z), (x2, thickness/2, z2), (x2, -thickness/2, z2), (x, -thickness/2, z)):
            positions.append(p); normals.append(normal); uvs.append((i/n, 1 if p[1] > 0 else 0))
        k=side+i*4; indices.extend((k,k+1,k+2,k,k+2,k+3))
    return positions, normals, uvs, indices


def stone_mesh(seed=2, rings=16, slices=32):
    rng = random.Random(seed)
    positions=[]; normals=[]; uvs=[]; indices=[]
    phase=rng.random()*math.tau
    for r in range(rings+1):
        v=r/rings; phi=v*math.pi
        for s in range(slices+1):
            u=s/slices; theta=u*math.tau
            variation=1 + .07*math.sin(theta*3+phase)*math.sin(phi)**2 + .035*math.sin(theta*7+phi*2)
            x=math.sin(phi)*math.cos(theta)*1.28*variation
            y=math.cos(phi)*.48*variation
            z=math.sin(phi)*math.sin(theta)*.9*variation
            positions.append((x,y,z))
            nx=x/(1.28*1.28); ny=y/(.48*.48); nz=z/(.9*.9); length=math.sqrt(nx*nx+ny*ny+nz*nz)
            normals.append((nx/length,ny/length,nz/length)); uvs.append((u,1-v))
    for r in range(rings):
        for s in range(slices):
            a=r*(slices+1)+s; b=a+slices+1
            indices.extend((a,b,a+1,a+1,b,b+1))
    return positions,normals,uvs,indices


def box_mesh(width, height, depth):
    x=width/2; y=height/2; z=depth/2
    faces=[((0,1,0),[(-x,y,-z),(x,y,-z),(x,y,z),(-x,y,z)]),((0,-1,0),[(-x,-y,z),(x,-y,z),(x,-y,-z),(-x,-y,-z)]),((0,0,1),[(-x,-y,z),(-x,y,z),(x,y,z),(x,-y,z)]),((0,0,-1),[(x,-y,-z),(x,y,-z),(-x,y,-z),(-x,-y,-z)]),((1,0,0),[(x,-y,z),(x,y,z),(x,y,-z),(x,-y,-z)]),((-1,0,0),[(-x,-y,-z),(-x,y,-z),(-x,y,z),(-x,-y,z)])]
    p=[]; n=[]; uv=[]; idx=[]
    for normal, verts in faces:
        k=len(p); p.extend(verts); n.extend([normal]*4); uv.extend([(0,0),(0,1),(1,1),(1,0)]); idx.extend((k,k+1,k+2,k,k+2,k+3))
    return p,n,uv,idx


def rounded_box_mesh(width, height, depth, radius=.07, segments=8):
    """Create a UV-mapped rounded cuboid with enough geometry for close PBR highlights."""
    positions, normals, uvs, indices = [], [], [], []
    half = np.array([width / 2, height / 2, depth / 2], dtype=np.float32)
    inner = np.maximum(half - radius, 0)
    faces = [
        (0, 1, 2, 1), (0, 1, 2, -1),
        (1, 0, 2, 1), (1, 0, 2, -1),
        (2, 0, 1, 1), (2, 0, 1, -1),
    ]
    for axis, u_axis, v_axis, sign in faces:
        start = len(positions)
        for v in range(segments + 1):
            for u in range(segments + 1):
                point = np.zeros(3, dtype=np.float32)
                point[axis] = half[axis] * sign
                point[u_axis] = (u / segments * 2 - 1) * half[u_axis]
                point[v_axis] = (v / segments * 2 - 1) * half[v_axis]
                nearest = np.clip(point, -inner, inner)
                delta = point - nearest
                length = float(np.linalg.norm(delta))
                normal = delta / length if length > 1e-6 else np.eye(3, dtype=np.float32)[axis] * sign
                rounded = nearest + normal * radius
                positions.append(tuple(float(x) for x in rounded))
                normals.append(tuple(float(x) for x in normal))
                uvs.append((u / segments, v / segments))
        stride = segments + 1
        for v in range(segments):
            for u in range(segments):
                a = start + v * stride + u
                b = a + 1
                c = a + stride
                d = c + 1
                if sign > 0:
                    indices.extend((a, c, b, b, c, d))
                else:
                    indices.extend((a, b, c, b, d, c))
    return positions, normals, uvs, indices


class Glb:
    def __init__(self):
        self.binary=bytearray(); self.views=[]; self.accessors=[]; self.images=[]; self.textures=[]; self.materials=[]; self.meshes=[]

    def align(self):
        while len(self.binary)%4: self.binary.append(0)

    def view(self, data, target=None):
        self.align(); offset=len(self.binary); self.binary.extend(data)
        view={"buffer":0,"byteOffset":offset,"byteLength":len(data)}
        if target: view["target"]=target
        self.views.append(view); return len(self.views)-1

    def accessor(self, values, component_type, kind, target, minmax=False):
        flat=np.asarray(values, dtype={5126:np.float32,5125:np.uint32,5123:np.uint16}[component_type])
        view=self.view(flat.tobytes(), target)
        count=len(values); item={"bufferView":view,"componentType":component_type,"count":count,"type":kind}
        if minmax:
            shaped=np.asarray(values); item["min"]=shaped.min(axis=0).astype(float).tolist(); item["max"]=shaped.max(axis=0).astype(float).tolist()
        self.accessors.append(item); return len(self.accessors)-1

    def material(self, texture_bytes, roughness, base=(1,1,1,1), metallic=0, normal_strength=3.0, normal_bytes=None, orm_bytes=None):
        texture_bytes = png_bytes(Image.open(io.BytesIO(texture_bytes)))
        if normal_bytes:
            normal_bytes = png_bytes(Image.open(io.BytesIO(normal_bytes)))
        if orm_bytes:
            orm_bytes = png_bytes(Image.open(io.BytesIO(orm_bytes)))
        image_view=self.view(texture_bytes)
        self.images.append({"bufferView":image_view,"mimeType":"image/jpeg"}); self.textures.append({"source":len(self.images)-1})
        base_texture = len(self.textures)-1
        normal_view=self.view(normal_bytes or normal_texture(texture_bytes, normal_strength))
        self.images.append({"bufferView":normal_view,"mimeType":"image/jpeg"}); self.textures.append({"source":len(self.images)-1})
        normal_map = len(self.textures)-1
        orm_view=self.view(orm_bytes or orm_texture(texture_bytes, roughness, metallic))
        self.images.append({"bufferView":orm_view,"mimeType":"image/jpeg"}); self.textures.append({"source":len(self.images)-1})
        orm_map = len(self.textures)-1
        self.materials.append({"pbrMetallicRoughness":{"baseColorFactor":base,"baseColorTexture":{"index":base_texture},"metallicFactor":1.0,"roughnessFactor":1.0,"metallicRoughnessTexture":{"index":orm_map}},"normalTexture":{"index":normal_map,"scale":1.0},"occlusionTexture":{"index":orm_map,"strength":1.0},"doubleSided":True})
        return len(self.materials)-1

    def mesh(self, geometry, material):
        p,n,uv,idx=geometry
        # Double-sided shading flips backface normals. Match geometric winding to
        # authored normals so top surfaces remain lit instead of turning black.
        triangles = np.asarray(idx).reshape(-1, 3)
        points = np.asarray(p)[triangles]
        face_normals = np.cross(points[:, 1] - points[:, 0], points[:, 2] - points[:, 0])
        authored_normals = np.asarray(n)[triangles].sum(axis=1)
        inward = (face_normals * authored_normals).sum(axis=1) < 0
        triangles[inward] = triangles[inward][:, [0, 2, 1]]
        idx = triangles.reshape(-1).tolist()
        tangents=[]
        for nx,ny,nz in n:
            if abs(ny)>.9: tangents.append((1,0,0,1))
            else:
                length=max(math.hypot(nx,nz),1e-6); tangents.append((nz/length,0,-nx/length,1))
        primitive={"attributes":{"POSITION":self.accessor(p,5126,"VEC3",34962,True),"NORMAL":self.accessor(n,5126,"VEC3",34962),"TANGENT":self.accessor(tangents,5126,"VEC4",34962),"TEXCOORD_0":self.accessor(uv,5126,"VEC2",34962)},"indices":self.accessor(idx,5125,"SCALAR",34963),"material":material}
        self.meshes.append({"primitives":[primitive]}); return len(self.meshes)-1

    def save(self, name, mesh_indices):
        nodes=[{"mesh":m} for m in mesh_indices]
        data={"asset":{"version":"2.0","generator":"Stack asset generator"},"scene":0,"scenes":[{"nodes":list(range(len(nodes)))}],"nodes":nodes,"meshes":self.meshes,"materials":self.materials,"textures":self.textures,"images":self.images,"accessors":self.accessors,"bufferViews":self.views,"buffers":[{"byteLength":len(self.binary)}]}
        encoded=json.dumps(data,separators=(",",":")).encode(); encoded+=b" "*((4-len(encoded)%4)%4); self.align()
        total=12+8+len(encoded)+8+len(self.binary)
        payload=struct.pack("<4sII",b"glTF",2,total)+struct.pack("<I4s",len(encoded),b"JSON")+encoded+struct.pack("<I4s",len(self.binary),b"BIN\x00")+self.binary
        temporary = OUT / (name + ".tmp")
        temporary.write_bytes(payload)
        temporary.replace(OUT / name)


def build_assets():
    OUT.mkdir(parents=True, exist_ok=True)
    paper_color=(SOURCE / "paper_color.jpg").read_bytes(); paper_normal=(SOURCE / "paper_normal.jpg").read_bytes(); paper_orm=packed_orm(SOURCE / "paper_roughness.jpg")
    cotton_white=white_paper_texture(paper_color)
    # A neutral river-stone treatment keeps the material consistent with its preview.
    stone_color=stone_texture(size=512)
    stone_orm=orm_texture(stone_color,.86)
    leather_color=(SOURCE / "leather_color.jpg").read_bytes(); leather_normal=(SOURCE / "leather_normal.jpg").read_bytes(); leather_orm=packed_orm(SOURCE / "leather_roughness.jpg")
    fabric_color=(SOURCE / "fabric_color.jpg").read_bytes(); fabric_normal=(SOURCE / "fabric_normal.jpg").read_bytes(); fabric_orm=packed_orm(SOURCE / "fabric_roughness.jpg", SOURCE / "fabric_ao.jpg")
    for variant in range(4):
        paper=Glb(); pm=paper.material(cotton_white,.9,normal_bytes=paper_normal,orm_bytes=paper_orm)
        geometry=paper_mesh(31 + variant, width=2.42 + variant * .025, depth=1.68 + (variant % 2) * .045, thickness=.045 + variant * .004)
        paper.save(f"cotton_paper_{variant + 1}.glb",[paper.mesh(geometry,pm)])
    for variant in range(3):
        stone=Glb(); sm=stone.material(stone_color,.86,normal_strength=.35,orm_bytes=stone_orm)
        stone.save(f"river_stone_{variant + 1}.glb",[stone.mesh(stone_mesh(47 + variant),sm)])
    for variant in range(4):
        cover_source=(leather_color,leather_normal,leather_orm) if variant % 2 == 0 else (fabric_color,fabric_normal,fabric_orm)
        book=Glb(); cover=book.material(cover_source[0],.68,base=(.22,.20,.18,1),normal_bytes=cover_source[1],orm_bytes=cover_source[2]); pages=book.material(paper_color,.88,base=(.82,.78,.68,1),normal_bytes=paper_normal,orm_bytes=paper_orm); meshes=[]
        width=2.22 + variant * .045; depth=1.47 + (variant % 2) * .05; page_height=.18 + variant * .018
        for geometry,material,y in [(rounded_box_mesh(width,.105,depth,.055),cover,page_height/2+.07),(rounded_box_mesh(width-.12,page_height,depth-.08,.035),pages,0),(rounded_box_mesh(width,.105,depth,.055),cover,-page_height/2-.07)]:
            p,n,uv,idx=geometry; p=[(x,yy+y,z) for x,yy,z in p]; meshes.append(book.mesh((p,n,uv,idx),material))
        book.save(f"library_book_{variant + 1}.glb",meshes)
    environment = OUT.parent / "environments"
    environment.mkdir(parents=True, exist_ok=True)
    (environment / "atelier.hdr").write_bytes((SOURCE / "white_home_studio_1k.hdr").read_bytes())
    for path in OUT.glob("*.glb"): print(path.relative_to(ROOT), path.stat().st_size)


if __name__ == "__main__":
    build_assets()
